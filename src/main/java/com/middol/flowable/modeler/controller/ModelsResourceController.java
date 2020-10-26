package com.middol.flowable.modeler.controller;


import org.flowable.ui.common.model.ResultListDataRepresentation;
import org.flowable.ui.modeler.model.ModelRepresentation;
import org.flowable.ui.modeler.rest.app.ModelsResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * 批量modelers操作 controller
 *
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app")
public class ModelsResourceController {

    @Resource
    private ModelsResource modelsResource;

    @RequestMapping(value = "/rest/models", method = RequestMethod.GET, produces = "application/json")
    public ResultListDataRepresentation getModels(@RequestParam(required = false) String filter,
                                                  @RequestParam(required = false) String sort,
                                                  @RequestParam(required = false) Integer modelType,
                                                  HttpServletRequest request) {
        // 过滤业务逻辑 begin
        // ...
        // 过滤业务逻辑 end
        return modelsResource.getModels(filter, sort, modelType, request);
    }

    @RequestMapping(value = "/rest/models", method = RequestMethod.POST, produces = "application/json")
    public ModelRepresentation createModel(@RequestBody ModelRepresentation modelRepresentation) {
        return modelsResource.createModel(modelRepresentation);
    }

    @RequestMapping(value = "/rest/import-process-model", method = RequestMethod.POST, produces = "application/json")
    public ModelRepresentation importProcessModel(HttpServletRequest request, @RequestParam("file") MultipartFile file) {
        return modelsResource.importProcessModel(request,file);
    }

    @RequestMapping(value = "/rest/models-for-app-definition", method = RequestMethod.GET, produces = "application/json")
    public ResultListDataRepresentation getModelsToIncludeInAppDefinition() {
        return modelsResource.getModelsToIncludeInAppDefinition();
    }

    @RequestMapping(value = "/rest/cmmn-models-for-app-definition", method = RequestMethod.GET, produces = "application/json")
    public ResultListDataRepresentation getCmmnModelsToIncludeInAppDefinition() {
        return modelsResource.getCmmnModelsToIncludeInAppDefinition();
    }

    @RequestMapping(value = "/rest/models/{modelId}/clone", method = RequestMethod.POST, produces = "application/json")
    public ModelRepresentation duplicateModel(@PathVariable String modelId, @RequestBody ModelRepresentation modelRepresentation) {
        return modelsResource.duplicateModel(modelId,modelRepresentation);
    }

}
