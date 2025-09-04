package org.wso2.carbon.apimgt.gateway.mediators;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ManagedLifecycle;
import org.apache.synapse.MessageContext;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.transport.passthru.util.RelayUtils;
import org.wso2.carbon.apimgt.api.APIConstants.AIAPIConstants;
import org.wso2.carbon.apimgt.api.gateway.ModelEndpointDTO;
import org.wso2.carbon.apimgt.api.gateway.LLMRPolicyConfigDTO;
import org.wso2.carbon.apimgt.api.gateway.CategoryEndpointDTO;
import org.wso2.carbon.apimgt.gateway.internal.DataHolder;
import org.wso2.carbon.apimgt.gateway.utils.GatewayUtils;
import org.wso2.carbon.apimgt.gateway.mediators.MistralService;
import org.wso2.carbon.apimgt.impl.APIConstants;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import javax.xml.namespace.QName;


public class LLMRouteMediator extends AbstractMediator implements ManagedLifecycle {
    private static final Log log = LogFactory.getLog(LLMRouteMediator.class);

    private String llmRouteConfigs;

    public void setLlmRouteConfigs(String llmRouteConfigs) {
        this.llmRouteConfigs = llmRouteConfigs;
    }

    @Override
    public void init(SynapseEnvironment synapseEnvironment) {
    }

    @Override
    public boolean mediate(MessageContext messageContext) {
        try {
            initializeCache(messageContext);
            
            if (!hasConfiguration()) {
                return true;
            }

            LLMRPolicyConfigDTO policyConfig = parseConfiguration(llmRouteConfigs);
            if (policyConfig == null) {
                return false;
            }

            return processRouting(messageContext, policyConfig);
        } catch (Exception e) {
            log.error("Error in LLMRouteMediator mediation", e);
            return false;
        }
    }

    private void initializeCache(MessageContext messageContext) {
        DataHolder.getInstance().initCache(GatewayUtils.getAPIKeyForEndpoints(messageContext));
    }

    private boolean hasConfiguration() {
        return llmRouteConfigs != null && !llmRouteConfigs.trim().isEmpty();
    }

    private boolean processRouting(MessageContext messageContext, LLMRPolicyConfigDTO policyConfig) {
        LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig = GatewayUtils.getLLMTargetConfig(messageContext, policyConfig);
        String classifiedCategory = classifyRequest(messageContext, targetConfig);
        ModelEndpointDTO selectedEndpoint = GatewayUtils.selectLLMEndpoint(targetConfig, messageContext, classifiedCategory);
        
        if (selectedEndpoint != null) {
            setEndpointProperties(messageContext, selectedEndpoint, policyConfig);
        } else {
            messageContext.setProperty(AIAPIConstants.TARGET_ENDPOINT, AIAPIConstants.REJECT_ENDPOINT);
        }
        return true;
    }

    private LLMRPolicyConfigDTO parseConfiguration(String config) {
        try {
            LLMRPolicyConfigDTO endpoints = new Gson().fromJson(config, LLMRPolicyConfigDTO.class);
            if (endpoints == null) {
                log.error("Failed to parse LLM routing configuration: null config");
            }
            return endpoints;
        } catch (JsonSyntaxException e) {
            log.error("Failed to parse LLM routing configuration", e);
            return null;
        }
    }

    private void setEndpointProperties(MessageContext messageContext, ModelEndpointDTO selectedEndpoint, LLMRPolicyConfigDTO endpoints) {
        messageContext.setProperty(AIAPIConstants.TARGET_ENDPOINT, selectedEndpoint.getEndpointId());
        
        Map<String, Object> llmRouteConfigs = new HashMap<>();
        llmRouteConfigs.put(AIAPIConstants.LLM_TARGET_MODEL_ENDPOINT, selectedEndpoint);
        llmRouteConfigs.put(AIAPIConstants.SUSPEND_DURATION, endpoints.getSuspendDuration() * AIAPIConstants.MILLISECONDS_IN_SECOND);
        messageContext.setProperty(AIAPIConstants.LLM_ROUTE_CONFIGS, llmRouteConfigs);
    }

    private String extractUserRequestContent(MessageContext messageContext) {
        try {
            org.apache.axis2.context.MessageContext msgContext = ((Axis2MessageContext) messageContext).getAxis2MessageContext();
            RelayUtils.buildMessage(msgContext);
            SOAPEnvelope envelope = msgContext.getEnvelope();
            OMElement jsonObject = envelope.getBody().getFirstChildWithName(new QName("jsonObject"));
            OMElement messages = jsonObject.getFirstChildWithName(new QName("messages"));
            OMElement content = messages.getFirstChildWithName(new QName("content"));
            return content.getText();
        } catch (Exception e) {
            return null;
        }
    }

    private String classifyRequest(MessageContext messageContext, LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig) {
        try {
            String content = extractUserRequestContent(messageContext);
            Set<String> availableCategories = getAvailableCategories(targetConfig);

            if (availableCategories == null || availableCategories.isEmpty() || content == null || content.trim().isEmpty()) {
                return null;
            }

            MistralService mistralService = new MistralService();
            if (!mistralService.isServiceAvailable()) {
                return null;
            }

            String prompt = buildClassificationPrompt(targetConfig, content);
            String response = mistralService.classifyRequest(prompt);

            return validateResponse(response, availableCategories);
        } catch (Exception e) {
            return null;
        }
    }

    private String buildClassificationPrompt(LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig, String content) {
        String categoryOptions = buildCategoryPrompt(targetConfig);
        String categoryNames = getCategoryNames(targetConfig);
        return "You are a strict classifier. These are the available categories:\n" + categoryOptions +
                "\n\nRespond with ONLY one of these category names: " + categoryNames + 
                "\nIf the request doesn't clearly fit any category, respond 'NONE'.\n" +
                "Request: " + content;
    }

    private String validateResponse(String response, Set<String> availableCategories) {
        if (response == null) return null;
        
        String cleanResponse = response.trim();
        if ("NONE".equals(cleanResponse)) return null;
        
        if (availableCategories.contains(cleanResponse)) {
            return cleanResponse;
        }

        for (String categoryName : availableCategories) {
            if (cleanResponse.toLowerCase().contains(categoryName.toLowerCase()) ||
                    categoryName.toLowerCase().contains(cleanResponse.toLowerCase())) {
                return categoryName;
            }
        }
        return null;
    }

    private Set<String> getAvailableCategories(LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig) {
        if (targetConfig == null || targetConfig.getCategories() == null) {
            return null;
        }

        Set<String> categories = new HashSet<>();
        for (CategoryEndpointDTO category : targetConfig.getCategories()) {
            if (category.isValid()) {
                categories.add(category.getName());
            }
        }
        return categories.isEmpty() ? null : categories;
    }

    private String buildCategoryPrompt(LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig) {
        if (targetConfig == null || targetConfig.getCategories() == null) {
            return "";
        }

        StringBuilder prompt = new StringBuilder();
        for (CategoryEndpointDTO category : targetConfig.getCategories()) {
            if (category.isValid()) {
                prompt.append("- ").append(category.getName());
                String context = category.getContext();
                if (context != null && !context.trim().isEmpty()) {
                    prompt.append(" - ").append(context);
                }
                prompt.append("\n");
            }
        }
        return prompt.toString().trim();
    }

    private String getCategoryNames(LLMRPolicyConfigDTO.LLMRDeploymentConfigDTO targetConfig) {
        if (targetConfig == null || targetConfig.getCategories() == null) {
            return "";
        }

        StringBuilder names = new StringBuilder();
        for (CategoryEndpointDTO category : targetConfig.getCategories()) {
            if (category.isValid()) {
                if (names.length() > 0) names.append(", ");
                names.append(category.getName());
            }
        }
        return names.toString();
    }

    @Override
    public boolean isContentAware() {
        return false;
    }

    public String getLlmRouteConfigs() {
        return llmRouteConfigs;
    }

    @Override
    public void destroy() {

    }

}



