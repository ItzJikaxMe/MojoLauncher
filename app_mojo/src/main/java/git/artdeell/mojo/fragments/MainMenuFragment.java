package git.artdeell.mojo.fragments;

import static git.artdeell.mojo.Tools.openPath;
import static git.artdeell.mojo.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.ProgressLayout;
import com.kdt.mcgui.mcVersionSpinner;

import git.artdeell.mojo.CustomControlsActivity;
import git.artdeell.mojo.R;

import git.artdeell.mojo.Tools;
import git.artdeell.mojo.contracts.OpenDocumentWithExtension;
import git.artdeell.mojo.extra.ExtraConstants;
import git.artdeell.mojo.extra.ExtraCore;
import git.artdeell.mojo.instances.Instance;
import git.artdeell.mojo.instances.Instances;
import git.artdeell.mojo.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data)->{
                if(data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mNewsButton = view.findViewById(R.id.news_button);
        Button mDiscordButton = view.findViewById(R.id.social_media_button);
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        Button mModpacksButton = view.findViewById(R.id.modpacks_button);
        Button mModsButton = view.findViewById(R.id.mods_button);
        Button mShadersButton = view.findViewById(R.id.shaders_button);
        Button mResourcePacksButton = view.findViewById(R.id.resourcepacks_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        mNewsButton.setOnClickListener(v -> Tools.openURL(requireActivity(), Tools.URL_HOME));
        mDiscordButton.setOnClickListener(v -> Tools.openURL(requireActivity(), getString(R.string.social_media_invite)));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation());
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> openGameDirectory(v.getContext()));
        mModpacksButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, null));
        mModsButton.setOnClickListener(v -> chooseAddonInstance(
                "mod", SearchModsFragment.class, SearchModsFragment.TAG));
        mShadersButton.setOnClickListener(v -> chooseAddonInstance(
                "shader", SearchShadersFragment.class, SearchShadersFragment.TAG));
        mResourcePacksButton.setOnClickListener(v -> chooseAddonInstance(
                "resourcepack", SearchResourcePacksFragment.class, SearchResourcePacksFragment.TAG));


        mNewsButton.setOnLongClickListener((v)->{
            Tools.swapFragment(requireActivity(), GamepadMapperFragment.class, GamepadMapperFragment.TAG, null);
            return true;
        });
    }


    private void chooseAddonInstance(String projectType,
                                     Class<? extends Fragment> destination,
                                     String fragmentTag) {
        final List<Instance> allInstances;
        try {
            allInstances = Instances.loadAllInstances();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Could not load installed Minecraft versions", Toast.LENGTH_LONG).show();
            return;
        }

        ArrayList<Instance> choices = new ArrayList<>();
        for (Instance instance : allInstances) {
            String gameVersion = instance.getGameVersion();
            if (gameVersion == null
                    || Instance.VERSION_LATEST_RELEASE.equalsIgnoreCase(gameVersion)
                    || Instance.VERSION_LATEST_SNAPSHOT.equalsIgnoreCase(gameVersion)) continue;
            if ("mod".equals(projectType) && !instance.hasFabricLoader()) continue;
            choices.add(instance);
        }

        if (choices.isEmpty()) {
            String message = "mod".equals(projectType)
                    ? "Install a Fabric version first to browse Fabric mods."
                    : "No installed Minecraft versions found.";
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show();
            return;
        }

        String[] labels = new String[choices.size()];
        for (int i = 0; i < choices.size(); i++) {
            Instance instance = choices.get(i);
            String name = Tools.validOrNullString(instance.name);
            if (name == null) name = "Minecraft";
            String loader = instance.getModLoader();
            labels[i] = name + "  •  " + instance.getGameVersion()
                    + (loader == null ? "" : "  •  " + loader);
        }

        String title = "mod".equals(projectType)
                ? "Choose a Fabric Minecraft version"
                : "Choose Minecraft version";
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(labels, (dialog, which) -> {
                    Instance selected = choices.get(which);
                    Bundle args = new Bundle();
                    args.putString(SearchModFragment.ARG_INSTANCE_ID, Instances.getInstanceId(selected));
                    args.putString(SearchModFragment.ARG_GAME_VERSION, selected.getGameVersion());
                    args.putString(SearchModFragment.ARG_LOADER,
                            "mod".equals(projectType) ? "fabric"
                                    : ("resourcepack".equals(projectType) ? "minecraft" : null));
                    Tools.swapFragment(requireActivity(), destination, fragmentTag, args);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openAddonDirectory(Context context, String folderName) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File directory = new File(instance.getGameDirectory(), folderName);
        if(FileUtils.ensureDirectorySilently(directory)) {
            openPath(context, directory, false);
        } else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if(instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if(FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        }else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if(!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        mModInstallerLauncher.launch(null);
    }
}
