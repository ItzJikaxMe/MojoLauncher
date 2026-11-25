package git.artdeell.mojo.fragments;

import static git.artdeell.mojo.Tools.openPath;
import static git.artdeell.mojo.Tools.shareLog;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.Toast;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
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
import git.artdeell.mojo.prefs.screens.LauncherPreferenceFragment;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private View mPlaySection;
    private View mDynamicContainer;

    private final ActivityResultLauncher<Object> mModInstallerLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("jar"), (data) -> {
                if (data != null) Tools.launchModInstaller(requireContext(), data);
            });

    public MainMenuFragment() {
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mPlaySection = view.findViewById(R.id.play_section);
        mDynamicContainer = view.findViewById(R.id.home_dynamic_container);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);

        View navPlay = view.findViewById(R.id.nav_play);
        View navMods = view.findViewById(R.id.nav_mods);
        View navResourcePacks = view.findViewById(R.id.nav_resourcepacks);
        View navShaders = view.findViewById(R.id.nav_shaders);
        View navModpacks = view.findViewById(R.id.nav_modpacks);
        View navSkinsCapes = view.findViewById(R.id.nav_skins_capes);
        View navControls = view.findViewById(R.id.nav_controls);
        View navMore = view.findViewById(R.id.nav_more);

        ImageButton editProfileButton = view.findViewById(R.id.edit_profile_button);
        View youtubeButton = requireActivity().findViewById(R.id.youtube_channel_button);
        if (youtubeButton != null) {
            youtubeButton.setOnClickListener(v -> Tools.openURL(requireActivity(),
                    "https://youtube.com/@immortallauncher?si=2mDw7nT4wpB9DIMy"));
        }
        View addVersionButton = view.findViewById(R.id.add_version_button);
        View playButton = view.findViewById(R.id.play_button);
        playButton.setBackgroundResource(R.drawable.launcher_play_button);
        if (playButton instanceof TextView) {
            TextView playLabel = (TextView) playButton;
            playLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            playLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.launcher_text));
        }

        setSelectedNavigation(navPlay);

        navPlay.setOnClickListener(v -> showPlaySection(navPlay));
        navMods.setOnClickListener(v -> {
            setSelectedNavigation(navMods);
            chooseAddonInstance("mod", SearchModsFragment.class, SearchModsFragment.TAG);
        });
        navResourcePacks.setOnClickListener(v -> {
            setSelectedNavigation(navResourcePacks);
            chooseAddonInstance("resourcepack", SearchResourcePacksFragment.class,
                    SearchResourcePacksFragment.TAG);
        });
        navShaders.setOnClickListener(v -> {
            setSelectedNavigation(navShaders);
            chooseAddonInstance("shader", SearchShadersFragment.class, SearchShadersFragment.TAG);
        });
        navModpacks.setOnClickListener(v -> {
            setSelectedNavigation(navModpacks);
            showDynamicFragment(new SearchModFragment(), SearchModFragment.TAG);
        });
        navSkinsCapes.setOnClickListener(v -> {
            setSelectedNavigation(navSkinsCapes);
            showDynamicFragment(new SkinCapeFragment(), "SkinCapeFragment");
        });
        navControls.setOnClickListener(v -> {
            setSelectedNavigation(navControls);
            startActivity(new Intent(requireContext(), CustomControlsActivity.class));
        });
        navMore.setOnClickListener(this::showMoreMenu);

        editProfileButton.setOnClickListener(v ->
                mVersionSpinner.openProfileEditor(requireActivity()));
        addVersionButton.setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ProfileTypeSelectFragment.class,
                        ProfileTypeSelectFragment.TAG, null));
        playButton.setOnClickListener(v ->
                ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
    }

    private void setSelectedNavigation(View selected) {
        int[] ids = {
                R.id.nav_play, R.id.nav_mods, R.id.nav_resourcepacks, R.id.nav_shaders,
                R.id.nav_modpacks, R.id.nav_skins_capes, R.id.nav_controls, R.id.nav_more
        };
        View root = getView();
        if (root == null) return;
        for (int id : ids) {
            View item = root.findViewById(id);
            if (item != null) item.setSelected(item == selected);
        }
    }

    private void showPlaySection(View selected) {
        setSelectedNavigation(selected);
        mDynamicContainer.setVisibility(View.GONE);
        mPlaySection.setVisibility(View.VISIBLE);
    }

    private void showDynamicFragment(Fragment fragment, String tag) {
        mPlaySection.setVisibility(View.GONE);
        mDynamicContainer.setVisibility(View.VISIBLE);
        getChildFragmentManager().beginTransaction()
                .setReorderingAllowed(true)
                .replace(R.id.home_dynamic_container, fragment, tag)
                .commit();
    }

    private void showMoreMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        menu.getMenu().add("About Immortal Launcher").setOnMenuItemClickListener(item -> {
            showAboutApp();
            return true;
        });
        menu.getMenu().add("Install JAR file").setOnMenuItemClickListener(item -> {
            runInstallerWithConfirmation();
            return true;
        });
        menu.getMenu().add("Share latest log").setOnMenuItemClickListener(item -> {
            shareLog(requireContext());
            return true;
        });
        menu.getMenu().add("Open game folder").setOnMenuItemClickListener(item -> {
            openGameDirectory(requireContext());
            return true;
        });
        menu.show();
    }

    private void showAboutApp() {
        new AlertDialog.Builder(requireContext())
                .setTitle("About Immortal Launcher")
                .setMessage("Immortal Launcher is your Minecraft Java launcher on Android.\n\n"
                        + "Manage game versions, mods, modpacks, shaders, resource packs, skins and capes.\n\n"
                        + "App owner: JikaxMc")
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void chooseAddonInstance(String projectType,
                                     Class<? extends Fragment> destination,
                                     String fragmentTag) {
        final List<Instance> allInstances;
        try {
            allInstances = Instances.loadAllInstances();
        } catch (IOException e) {
            Toast.makeText(requireContext(), "Could not load installed Minecraft versions",
                    Toast.LENGTH_LONG).show();
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
                    args.putString(SearchModFragment.ARG_INSTANCE_ID,
                            Instances.getInstanceId(selected));
                    args.putString(SearchModFragment.ARG_GAME_VERSION, selected.getGameVersion());
                    args.putString(SearchModFragment.ARG_LOADER,
                            "mod".equals(projectType) ? "fabric"
                                    : ("resourcepack".equals(projectType) ? "minecraft" : null));
                    Fragment target = getChildFragmentManager().getFragmentFactory()
                            .instantiate(requireContext().getClassLoader(), destination.getName());
                    target.setArguments(args);
                    showDynamicFragment(target, fragmentTag);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void openGameDirectory(Context context) {
        Instance instance = Instances.loadSelectedInstance();
        if (instance == null) {
            Toast.makeText(context, R.string.no_instance, Toast.LENGTH_LONG).show();
            return;
        }
        File gameDirectory = instance.getGameDirectory();
        if (FileUtils.ensureDirectorySilently(gameDirectory)) {
            openPath(context, gameDirectory, false);
        } else {
            Toast.makeText(context, R.string.gamedir_open_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        ExtraCore.setValue(ExtraConstants.REFRESH_ACCOUNT_SPINNER, true);
    }

    private void runInstallerWithConfirmation() {
        if (!ProgressLayout.ensureNoOngoingTasks(requireContext())) return;
        mModInstallerLauncher.launch(null);
    }
}
