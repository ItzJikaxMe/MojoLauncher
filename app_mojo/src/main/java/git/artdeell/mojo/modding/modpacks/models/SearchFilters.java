package git.artdeell.mojo.modding.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    /** Modrinth project type: mod, modpack, shader, or resourcepack. */
    public String projectType;
    public String name;
    @Nullable public String mcVersion;

}
