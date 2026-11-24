package git.artdeell.mojo.modding.modpacks.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import android.content.Context;
import android.widget.Toast;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.downloader.Downloader;
import git.artdeell.mojo.downloader.TaskMetadata;
import git.artdeell.mojo.tasks.mojson.mirrors.DownloadMirror;
import git.artdeell.mojo.modding.fabric.FabriclikeUtils;
import git.artdeell.mojo.modding.forge.ForgelikeUtils;
import git.artdeell.mojo.modding.lwjgl3ify.Lwjgl3ifyUtils;
import git.artdeell.mojo.modding.modpacks.api.modloader.FabriclikeLoaderInstaller;
import git.artdeell.mojo.modding.modpacks.api.modloader.ForgelikeLoaderInstaller;
import git.artdeell.mojo.modding.modpacks.api.modloader.LoaderInstaller;
import git.artdeell.mojo.modding.modpacks.api.modloader.Lwjgl3ifyLoaderInstaller;
import git.artdeell.mojo.modding.modpacks.models.Constants;
import git.artdeell.mojo.modding.modpacks.models.ModDetail;
import git.artdeell.mojo.modding.modpacks.models.ModItem;
import git.artdeell.mojo.modding.modpacks.models.ModrinthIndex;
import git.artdeell.mojo.modding.modpacks.models.SearchFilters;
import git.artdeell.mojo.modding.modpacks.models.SearchResult;
import git.artdeell.mojo.utils.FileUtils;
import git.artdeell.mojo.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        facetString.append(String.format("[\"project_type:%s\"]", searchFilters.isModpack ? "modpack" : "mod"));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            String projectType = hit.get("project_type").getAsString();
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    projectType.equals("modpack"),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.has("icon_url") && !hit.get("icon_url").isJsonNull() ? hit.get("icon_url").getAsString() : null
            );
            items[i].projectType = projectType;
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {

        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), JsonArray.class);
        if(response == null) return null;
        System.out.println(response);
        String[] names = new String[response.size()];
        String[] mcNames = new String[response.size()];
        String[] urls = new String[response.size()];
        String[] hashes = new String[response.size()];
        String[] fileNames = new String[response.size()];

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = version.get("name").getAsString();
            mcNames[i] = version.get("game_versions").getAsJsonArray().get(0).getAsString();
            JsonArray files = version.getAsJsonArray("files");
            int primaryIndex = 0;
            for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
                JsonObject candidate = files.get(fileIndex).getAsJsonObject();
                if (candidate.has("primary") && candidate.get("primary").getAsBoolean()) {
                    primaryIndex = fileIndex;
                    break;
                }
            }
            JsonObject primaryFile = files.get(primaryIndex).getAsJsonObject();
            urls[i] = primaryFile.get("url").getAsString();
            fileNames[i] = primaryFile.get("filename").getAsString();
            // Assume there may not be hashes, in case the API changes
            JsonObject hashesMap = primaryFile.has("hashes") && primaryFile.get("hashes").isJsonObject()
                    ? primaryFile.getAsJsonObject("hashes") : null;
            if(hashesMap == null || hashesMap.get("sha1") == null){
                hashes[i] = null;
                continue;
            }

            hashes[i] = hashesMap.get("sha1").getAsString();
        }

        ModDetail detail = new ModDetail(item, names, mcNames, urls, hashes);
        detail.versionFileNames = fileNames;
        return detail;
    }


    @Override
    public void handleProjectInstallation(Context context, ModDetail detail, int selectedVersion) {
        if ("modpack".equals(detail.projectType)) {
            ModpackApi.super.handleProjectInstallation(context, detail, selectedVersion);
            return;
        }
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.global_waiting);
        git.artdeell.mojo.MojoApplication.sExecutorService.execute(() -> {
            try {
                Instance instance = Instances.loadSelectedInstance();
                if (instance == null) throw new IOException("No Minecraft instance selected");
                String folderName;
                switch (detail.projectType) {
                    case "mod": folderName = "mods"; break;
                    case "shader": folderName = "shaderpacks"; break;
                    case "resourcepack": folderName = "resourcepacks"; break;
                    default: throw new IOException("Unsupported Modrinth project type: " + detail.projectType);
                }
                if (selectedVersion < 0 || selectedVersion >= detail.versionUrls.length)
                    throw new IOException("Invalid project version selected");
                File destinationDirectory = new File(instance.getGameDirectory(), folderName);
                FileUtils.ensureDirectory(destinationDirectory);
                String fileName = detail.versionFileNames[selectedVersion];
                if (fileName == null || fileName.trim().isEmpty()) {
                    fileName = new File(new URL(detail.versionUrls[selectedVersion]).getPath()).getName();
                }
                fileName = new File(fileName).getName();
                File destination = new File(destinationDirectory, fileName);
                if (destination.exists()) {
                    String base = fileName;
                    int dot = fileName.lastIndexOf('.');
                    String extension = dot > 0 ? fileName.substring(dot) : "";
                    if (dot > 0) base = fileName.substring(0, dot);
                    int suffix = 2;
                    do {
                        destination = new File(destinationDirectory, base + " (" + suffix++ + ")" + extension);
                    } while (destination.exists());
                }
                HttpURLConnection connection = (HttpURLConnection) new URL(detail.versionUrls[selectedVersion]).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.setInstanceFollowRedirects(true);
                connection.connect();
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300)
                    throw new IOException("Download failed: HTTP " + connection.getResponseCode());
                try (InputStream input = connection.getInputStream();
                     FileOutputStream output = new FileOutputStream(destination)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                    output.flush();
                } finally {
                    connection.disconnect();
                }
                Tools.runOnUiThread(() -> Toast.makeText(context,
                        context.getString(R.string.generic_install) + ": " + fileName,
                        Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Tools.showErrorRemote(context, R.string.modpack_install_download_failed, e);
            } finally {
                ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            }
        });
    }

    @Override
    public LoaderInstaller installModpack(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installMrpack);
    }

    public LoaderInstaller installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installMrpack);
    }

    private static LoaderInstaller createInfo(ModrinthIndex modrinthIndex, File installDestination) throws IOException {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ForgelikeLoaderInstaller(ForgelikeUtils.FORGE_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new FabriclikeLoaderInstaller(FabriclikeUtils.FABRIC_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new FabriclikeLoaderInstaller(FabriclikeUtils.QUILT_UTILS, mcVersion, modLoaderVersion);
        } else if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ForgelikeLoaderInstaller(ForgelikeUtils.NEOFORGE_UTILS, mcVersion, modLoaderVersion);
        } else if(dependencies.size() == 1) {
            // "Vanilla" pack. Possibly GT:NH, let's try to detect lwjgl3ify
            File lwjgl3ifyJar = Lwjgl3ifyUtils.detectLwjgl3ifyJar(installDestination);
            if(lwjgl3ifyJar != null) return new Lwjgl3ifyLoaderInstaller(lwjgl3ifyJar);
        }

        return null;
    }

    private LoaderInstaller installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            try {
                new ModrinthDownloader().startDownloads(modrinthIndex.files, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex, instanceDestination);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }

    static class ModrinthDownloader extends Downloader {
        public ModrinthDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        protected void startDownloads(ModrinthIndex.ModrinthIndexFile[] indexFiles, File instanceDestination) throws IOException, InterruptedException {
            String absoluteInstancePath = instanceDestination.getAbsolutePath();
            ArrayList<TaskMetadata> taskMetadatas = new ArrayList<>(indexFiles.length);
            for(ModrinthIndex.ModrinthIndexFile file : indexFiles) {
                File targetPath = new File(instanceDestination, file.path);
                if(!targetPath.getAbsolutePath().startsWith(absoluteInstancePath)) throw new IOException("Bad path!");
                FileUtils.ensureParentDirectory(targetPath);
                taskMetadatas.add(new TaskMetadata(
                        targetPath, new URL(file.downloads[0]), // TODO source selection
                        file.fileSize, file.hashes.sha1,
                        DownloadMirror.DOWNLOAD_CLASS_NONE
                ));
            }
            runDownloads(taskMetadatas);
        }
    }
}
