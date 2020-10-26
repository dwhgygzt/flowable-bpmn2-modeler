package com.middol.flowable.modeler.controller;


import org.flowable.ui.modeler.domain.ModelInformation;
import org.flowable.ui.modeler.rest.app.ModelRelationResource;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app")
public class ModelRelationResourceController {

    @Resource
    private ModelRelationResource modelRelationResource;

    @RequestMapping(value = "/rest/models/{modelId}/parent-relations", method = RequestMethod.GET, produces = "application/json")
    public List<ModelInformation> getModelRelations(@PathVariable String modelId) {
        return modelRelationResource.getModelRelations(modelId);
    }

}
