package com.guzt.flowable.modeler.controller;


import org.flowable.ui.common.model.ResultListDataRepresentation;
import org.flowable.ui.modeler.rest.app.ModelHistoryResource;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 单个modelers历史查看操作 controller
 *
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app")
public class ModelHistoryResourceController {

    @Resource
    private ModelHistoryResource modelHistoryResource;

    @RequestMapping(value = "/rest/models/{modelId}/history", method = RequestMethod.GET, produces = "application/json")
    public ResultListDataRepresentation getModelHistoryCollection(@PathVariable String modelId, @RequestParam(value = "includeLatestVersion", required = false) Boolean includeLatestVersion) {
        return modelHistoryResource.getModelHistoryCollection(modelId, includeLatestVersion);
    }

}
