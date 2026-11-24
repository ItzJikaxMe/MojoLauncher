package git.artdeell.mojo.instances;

import java.io.File;

public class DisplayInstance {
    protected transient File mInstanceRoot;
    public String name;
    public String versionId;
    public String icon;
    /** Minecraft base version and loader for loader-specific instances. */
    public String gameVersion;
    public String modLoader;

    public String getGameVersion() {
        if (gameVersion != null && !gameVersion.trim().isEmpty()) return gameVersion;
        String loader = getModLoader();
        if (loader != null && versionId != null) {
            int separator = versionId.lastIndexOf('-');
            if (separator >= 0 && separator < versionId.length() - 1)
                return versionId.substring(separator + 1);
        }
        return versionId;
    }

    public String getModLoader() {
        if (modLoader != null && !modLoader.trim().isEmpty()) return modLoader;
        if ("fabric".equals(icon)) {
            if (versionId != null && versionId.startsWith("legacy-fabric")) return "legacy_fabric";
            return "fabric";
        }
        if ("quilt".equals(icon)) return "quilt";
        if ("legacy_fabric".equals(icon)) return "legacy_fabric";
        return null;
    }

    public boolean hasFabricLoader() {
        return "fabric".equals(getModLoader());
    }

    protected void sanitize() {
        sanitizeIcon();
    }

    protected DisplayInstance() {
    }

    protected File getInstanceIconLocation() {
        return new File(mInstanceRoot, "icon.webp");
    }

    private void sanitizeIcon() {
        if(!InstanceIconProvider.hasStaticIcon(icon)) {
            icon = InstanceIconProvider.FALLBACK_ICON_NAME;
        }
    }
}
