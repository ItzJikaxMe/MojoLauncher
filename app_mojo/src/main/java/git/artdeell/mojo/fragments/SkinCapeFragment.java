package git.artdeell.mojo.fragments;

import android.annotation.SuppressLint;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import git.artdeell.mojo.R;
import git.artdeell.mojo.Tools;
import git.artdeell.mojo.MojoApplication;
import git.artdeell.mojo.authenticator.accounts.Account;
import git.artdeell.mojo.authenticator.accounts.Accounts;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class SkinCapeFragment extends Fragment {
    private WebView preview;
    private TextView status;
    private String pendingType = "skin";
    private File savedSkin;
    private File savedCape;

    private final ActivityResultLauncher<String> imagePicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(), this::onImagePicked);

    public SkinCapeFragment() {
        super(R.layout.fragment_skin_cape);
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        preview = view.findViewById(R.id.skin_cape_preview);
        status = view.findViewById(R.id.skin_cape_status);
        Button skinButton = view.findViewById(R.id.skin_choose_button);
        Button capeButton = view.findViewById(R.id.cape_choose_button);

        File appFolder = new File(requireContext().getFilesDir(), "skins");
        savedSkin = new File(appFolder, "skin.png");
        savedCape = new File(appFolder, "cape.png");

        WebSettings settings = preview.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(false);
        preview.setBackgroundColor(0xFF151020);
        preview.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView webView, String url) {
                restorePreview();
            }
        });
        preview.loadDataWithBaseURL(
                "https://cdn.jsdelivr.net/npm/skinview3d@3.4.1/",
                viewerHtml(), "text/html", "UTF-8", null);

        skinButton.setOnClickListener(v -> {
            pendingType = "skin";
            imagePicker.launch("image/png");
        });
        capeButton.setOnClickListener(v -> {
            pendingType = "cape";
            imagePicker.launch("image/png");
        });

        status.setText((savedSkin.exists() ? "Skin saved" : "No skin selected")
                + "  •  " + (savedCape.exists() ? "Cape saved" : "No cape selected"));
    }

    private void restorePreview() {
        if (!isAdded() || preview == null) return;
        try {
            if (savedSkin != null && savedSkin.isFile()) {
                preview.evaluateJavascript("window.showSkin('"
                        + Base64.encodeToString(readFile(savedSkin), Base64.NO_WRAP)
                        + "')", null);
            }
            if (savedCape != null && savedCape.isFile()) {
                preview.evaluateJavascript("window.showCape('"
                        + Base64.encodeToString(readFile(savedCape), Base64.NO_WRAP)
                        + "')", null);
            }
        } catch (IOException e) {
            status.setText("Could not restore saved preview.");
        }
    }

    private byte[] readFile(File file) throws IOException {
        try (InputStream input = new java.io.FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private void onImagePicked(Uri uri) {
        if (uri == null || !isAdded()) return;
        try (InputStream input = requireContext().getContentResolver().openInputStream(uri);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (input == null) throw new IOException("Cannot open selected image");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                if (output.size() > 5 * 1024 * 1024)
                    throw new IOException("PNG must be 5 MB or smaller");
            }
            byte[] bytes = output.toByteArray();
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            boolean valid = "skin".equals(pendingType)
                    ? ((options.outWidth == 64 && (options.outHeight == 64 || options.outHeight == 32))
                        || (options.outWidth > 0 && options.outHeight > 0
                            && options.outWidth % 64 == 0 && options.outHeight % 32 == 0))
                    : (options.outWidth > 0 && options.outHeight > 0
                        && options.outWidth * 2 == options.outHeight * 4);
            if (!valid) throw new IOException("Invalid PNG dimensions for " + pendingType);

            File appFolder = new File(requireContext().getFilesDir(), "skins");
            if (!appFolder.exists() && !appFolder.mkdirs())
                throw new IOException("Cannot create skin storage");
            File appTarget = new File(appFolder, pendingType + ".png");
            try (FileOutputStream file = new FileOutputStream(appTarget)) { file.write(bytes); }

            if ("skin".equals(pendingType)) savedSkin = appTarget;
            else savedCape = appTarget;

            String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String function = "skin".equals(pendingType) ? "showSkin" : "showCape";
            if (preview != null) preview.evaluateJavascript("window." + function + "('" + encoded + "')", null);

            status.setText("Saved on this device. Applying to selected Minecraft profile…");
            applyToSelectedGame(bytes, pendingType);
        } catch (Exception e) {
            Toast.makeText(requireContext(), "Import failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void applyToSelectedGame(byte[] bytes, String type) {
        final Instance instance = Instances.loadSelectedInstance();
        final Account account = Accounts.getCurrent();
        if (instance == null || account == null || !Tools.isValidString(account.username)) {
            status.setText("Saved in launcher. Select a game version and player profile to apply in game.");
            return;
        }
        final String loader = instance.getModLoader();
        if (!"fabric".equals(loader) && !"quilt".equals(loader)) {
            status.setText("Saved. Select a Fabric or Quilt profile to enable in-game skins.");
            return;
        }
        final String username = account.username.replaceAll("[^A-Za-z0-9_]", "_");
        final File gameDir = instance.getGameDirectory();
        final String kind = type;
        MojoApplication.sExecutorService.execute(() -> {
            try {
                File localRoot = new File(gameDir, "CustomSkinLoader/LocalSkin");
                File targetDir = new File(localRoot, "skin".equals(kind) ? "skins" : "capes");
                if (!targetDir.exists() && !targetDir.mkdirs())
                    throw new IOException("Cannot create CustomSkinLoader folder");
                try (FileOutputStream output = new FileOutputStream(new File(targetDir, username + ".png"))) {
                    output.write(bytes);
                }
                installCustomSkinLoader(instance, gameDir);
                if (isAdded()) requireActivity().runOnUiThread(() ->
                        status.setText(("skin".equals(kind) ? "Skin" : "Cape")
                                + " saved and applied to " + instance.name + ". Launch this profile to see it."));
            } catch (Exception e) {
                if (isAdded()) requireActivity().runOnUiThread(() ->
                        status.setText("Saved in launcher, but in-game setup failed: " + e.getMessage()));
            }
        });
    }

    private void installCustomSkinLoader(Instance instance, File gameDir) throws Exception {
        File mods = new File(gameDir, "mods");
        if (!mods.exists() && !mods.mkdirs()) throw new IOException("Cannot create mods folder");
        File[] existing = mods.listFiles((dir, name) ->
                name.toLowerCase(java.util.Locale.ROOT).contains("customskinloader")
                        && name.toLowerCase(java.util.Locale.ROOT).endsWith(".jar"));
        if (existing != null && existing.length > 0) return;

        String gameVersion = instance.getGameVersion();
        String loader = instance.getModLoader();
        String query = "https://api.modrinth.com/v2/project/customskinloader/version?game_versions="
                + URLEncoder.encode("[\"" + gameVersion + "\"]", "UTF-8")
                + "&loaders=" + URLEncoder.encode("[\"" + loader + "\"]", "UTF-8");
        HttpURLConnection api = (HttpURLConnection) new URL(query).openConnection();
        api.setConnectTimeout(15000);
        api.setReadTimeout(20000);
        api.setRequestProperty("User-Agent", "MojoLauncher/Skins");
        try {
            if (api.getResponseCode() != 200) throw new IOException("Modrinth returned " + api.getResponseCode());
            ByteArrayOutputStream jsonBytes = new ByteArrayOutputStream();
            try (InputStream input = api.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) jsonBytes.write(buffer, 0, count);
            }
            JSONArray versions = new JSONArray(jsonBytes.toString("UTF-8"));
            if (versions.length() == 0) throw new IOException("No compatible CustomSkinLoader release for " + gameVersion);
            JSONObject version = versions.getJSONObject(0);
            JSONArray files = version.getJSONArray("files");
            JSONObject selected = files.getJSONObject(0);
            for (int i = 0; i < files.length(); i++) {
                JSONObject candidate = files.getJSONObject(i);
                if (candidate.optBoolean("primary", false)) { selected = candidate; break; }
            }
            String fileName = selected.getString("filename");
            String download = selected.getString("url");
            File target = new File(mods, fileName);
            File temporary = new File(mods, fileName + ".download");
            HttpURLConnection fileConnection = (HttpURLConnection) new URL(download).openConnection();
            fileConnection.setConnectTimeout(15000);
            fileConnection.setReadTimeout(30000);
            fileConnection.setRequestProperty("User-Agent", "MojoLauncher/Skins");
            try {
                if (fileConnection.getResponseCode() != 200)
                    throw new IOException("Skin mod download failed: " + fileConnection.getResponseCode());
                try (InputStream input = fileConnection.getInputStream();
                     FileOutputStream output = new FileOutputStream(temporary)) {
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                }
            } finally {
                fileConnection.disconnect();
            }
            if (target.exists() && !target.delete()) throw new IOException("Cannot replace old skin mod");
            if (!temporary.renameTo(target)) throw new IOException("Cannot finish installing skin mod");
        } finally {
            api.disconnect();
        }
    }

    private String viewerHtml() {
        return "<!doctype html><html><head><meta name='viewport' content='width=device-width, initial-scale=1'>"
                + "<style>html,body{margin:0;width:100%;height:100%;overflow:hidden;background:#151020}"
                + "canvas{width:100%;height:100%;display:block}</style>"
                + "<script src='bundles/skinview3d.bundle.js'></script></head><body><canvas id='skin'></canvas>"
                + "<script>let viewer=null,pendingSkin=null,pendingCape=null;"
                + "function init(){if(!window.skinview3d||viewer)return;"
                + "const c=document.getElementById('skin');viewer=new skinview3d.SkinViewer({canvas:c,width:360,height:420});"
                + "viewer.controls.enableRotate=true;viewer.controls.enableZoom=true;"
                + "viewer.animation=new skinview3d.IdleAnimation();"
                + "if(pendingSkin)viewer.loadSkin(pendingSkin);if(pendingCape)viewer.loadCape(pendingCape);}"
                + "window.showSkin=function(data){pendingSkin='data:image/png;base64,'+data;if(!viewer)init();if(viewer)viewer.loadSkin(pendingSkin);};"
                + "window.showCape=function(data){pendingCape='data:image/png;base64,'+data;if(!viewer)init();if(viewer)viewer.loadCape(pendingCape);};"
                + "window.addEventListener('load',init);</script></body></html>";
    }

    @Override
    public void onDestroyView() {
        if (preview != null) {
            preview.loadUrl("about:blank");
            preview.destroy();
            preview = null;
        }
        super.onDestroyView();
    }
}
