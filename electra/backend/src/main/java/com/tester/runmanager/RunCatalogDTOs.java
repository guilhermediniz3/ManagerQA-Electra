package com.tester.runmanager;

import java.util.List;

import com.tester.runmanager.entity.*;

/**
 * O que a tela de cadastro do RunManager troca com o servidor.
 *
 * São registros, e não classes com getter e setter, porque nenhum deles tem
 * comportamento: entram como o formulário mandou e saem como o banco guardou.
 */
public final class RunCatalogDTOs {

    private RunCatalogDTOs() {
    }

    public record SystemDTO(Long id, String name, String key, String repository, String branch,
                            String localPath, String selectionScript, String variables,
                            String versionFlag, boolean active, Integer position,
                            List<VersionDTO> versions) {

        public static SystemDTO de(RunSystem s, List<VersionDTO> versoes) {
            return new SystemDTO(s.getId(), s.getName(), s.getKey(), s.getRepository(),
                    s.getBranch(), s.getLocalPath(), s.getSelectionScript(), s.getVariables(),
                    s.getVersionFlag(), s.isActive(), s.getPosition(), versoes);
        }
    }

    public record VersionDTO(Long id, Long systemId, String name, String key,
                             boolean active, Integer position) {

        public static VersionDTO de(RunVersion v) {
            return new VersionDTO(v.getId(), v.getSystem() == null ? null : v.getSystem().getId(),
                    v.getName(), v.getKey(), v.isActive(), v.getPosition());
        }
    }

    public record EnvironmentDTO(Long id, String name, String key, boolean production,
                                 String variables, boolean active, Integer position) {

        public static EnvironmentDTO de(RunEnvironment e) {
            return new EnvironmentDTO(e.getId(), e.getName(), e.getKey(), e.isProduction(),
                    e.getVariables(), e.isActive(), e.getPosition());
        }
    }

    public record TagDTO(Long id, String name, String key, String description,
                        boolean active, Integer position) {

        public static TagDTO de(RunTag t) {
            return new TagDTO(t.getId(), t.getName(), t.getKey(), t.getDescription(),
                    t.isActive(), t.getPosition());
        }
    }

    /** Tudo o que a tela precisa para montar os seletores numa chamada só. */
    public record CatalogoDTO(List<SystemDTO> systems, List<EnvironmentDTO> environments,
                              List<TagDTO> tags) {
    }
}
