package org.wso2.carbon.apimgt.api.gateway;

/**
 * DTO representing a category endpoint configuration for LLM routing.
 */
public class CategoryEndpointDTO {

    private String name;
    private String context;
    private String description;
    private String model;
    private String endpointId;

    /**
     * Gets the category name.
     *
     * @return the category name
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the category name.
     *
     * @param name the category name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the category context.
     *
     * @return the category context
     */
    public String getContext() {
        return context;
    }

    /**
     * Sets the category context.
     *
     * @param context the category context to set
     */
    public void setContext(String context) {
        this.context = context;
    }

    /**
     * Gets the category description.
     *
     * @return the category description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Sets the category description.
     *
     * @param description the category description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * Gets the model name.
     *
     * @return the model name
     */
    public String getModel() {
        return model;
    }

    /**
     * Sets the model name.
     *
     * @param model the model name to set
     */
    public void setModel(String model) {
        this.model = model;
        this.cachedModelEndpoint = null; 
    }

    /**
     * Gets the endpoint ID.
     *
     * @return the endpoint ID
     */
    public String getEndpointId() {
        return endpointId;
    }

    /**
     * Sets the endpoint ID.
     *
     * @param endpointId the endpoint ID to set
     */
    public void setEndpointId(String endpointId) {
        this.endpointId = endpointId;
        this.cachedModelEndpoint = null; // Clear cache when endpoint changes
    }

    private ModelEndpointDTO cachedModelEndpoint; // Cache to avoid repeated object creation

    /**
     * Converts this CategoryEndpointDTO to a ModelEndpointDTO.
     * Uses caching to improve performance.
     *
     * @return the corresponding ModelEndpointDTO
     */
    public ModelEndpointDTO toModelEndpointDTO() {
        if (cachedModelEndpoint == null && isValid()) {
            cachedModelEndpoint = new ModelEndpointDTO();
            cachedModelEndpoint.setModel(this.model);
            cachedModelEndpoint.setEndpointId(this.endpointId);
        }
        return cachedModelEndpoint;
    }

    /**
     * Validates if this category endpoint has all required fields.
     *
     * @return true if valid, false otherwise
     */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty()
                && model != null && !model.trim().isEmpty()
                && endpointId != null && !endpointId.trim().isEmpty();
    }
}
