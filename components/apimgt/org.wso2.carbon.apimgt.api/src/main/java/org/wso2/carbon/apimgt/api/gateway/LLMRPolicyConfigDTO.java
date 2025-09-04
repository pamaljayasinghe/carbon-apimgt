package org.wso2.carbon.apimgt.api.gateway;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class LLMRPolicyConfigDTO {
    private static final long DEFAULT_SUSPEND_DURATION = 0L;
    private LLMRDeploymentConfigDTO production;
    private LLMRDeploymentConfigDTO sandbox;
    private Long suspendDuration = DEFAULT_SUSPEND_DURATION;

    public static class LLMRDeploymentConfigDTO {
        private ModelEndpointDTO defaultModel;
        private List<CategoryEndpointDTO> categories;
        private Map<String, ModelEndpointDTO> categoryCache;

        public ModelEndpointDTO selectEndpointForCategory(String categoryName) {
            if (categoryName == null || categoryName.trim().isEmpty()) {
                return getValidDefaultModel();
            }

            if (categoryCache == null) {
                buildCategoryCache();
            }

            ModelEndpointDTO cachedEndpoint = categoryCache.get(categoryName);
            if (cachedEndpoint != null) {
                return cachedEndpoint;
            }

            return getValidDefaultModel();
        }

        private void buildCategoryCache() {
            categoryCache = new HashMap<>();
            if (categories != null) {
                for (CategoryEndpointDTO category : categories) {
                    if (category != null && category.isValid()) {
                        categoryCache.put(category.getName(), category.toModelEndpointDTO());
                    }
                }
            }
        }

        private ModelEndpointDTO getValidDefaultModel() {
            if (defaultModel != null && isValidModelEndpoint(defaultModel)) {
                return defaultModel;
            }
            return null;
        }

        private boolean isValidModelEndpoint(ModelEndpointDTO model) {
            return model != null
                    && model.getModel() != null && !model.getModel().trim().isEmpty()
                    && model.getEndpointId() != null && !model.getEndpointId().trim().isEmpty();
        }

        public ModelEndpointDTO getDefaultModel() {
            return defaultModel;
        }

        public void setDefaultModel(ModelEndpointDTO defaultModel) {
            this.defaultModel = defaultModel;
        }

        public List<CategoryEndpointDTO> getCategories() {
            return categories;
        }

        public void setCategories(List<CategoryEndpointDTO> categories) {
            this.categories = categories;
            this.categoryCache = null; 
        }

        /**
         * Gets the number of valid categories.
         * @return count of valid categories
         */
        public int getCategoryCount() {
            if (categories == null) {
                return 0;
            }
            return (int) categories.stream()
                    .filter(category -> category != null && category.isValid())
                    .count();
        }

        /**
         * Checks if this deployment config has any valid endpoints.
         * @return true if has valid endpoints, false otherwise
         */
        public boolean hasValidEndpoints() {
            return (defaultModel != null && isValidModelEndpoint(defaultModel)) 
                    || getCategoryCount() > 0;
        }
    }

    public LLMRDeploymentConfigDTO getProduction() {
        return production;
    }

    public void setProduction(LLMRDeploymentConfigDTO production) {
        this.production = production;
    }

    public LLMRDeploymentConfigDTO getSandbox() {
        return sandbox;
    }

    public void setSandbox(LLMRDeploymentConfigDTO sandbox) {
        this.sandbox = sandbox;
    }

    public Long getSuspendDuration() {
        return suspendDuration;
    }

    public void setSuspendDuration(Long suspendDuration) {
        this.suspendDuration = suspendDuration;
    }
}
