package com.middol.flowable.modeler.controller;


import org.flowable.ui.modeler.model.FormSaveRepresentation;
import org.flowable.ui.modeler.model.form.FormRepresentation;
import org.flowable.ui.modeler.rest.app.FormResource;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * 单个form操作 controller
 *
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app/rest/form-models")
public class FormResourceController {

    @Resource
    private FormResource formResource;

    @RequestMapping(value = "/{formId}", method = RequestMethod.GET, produces = "application/json")
    public FormRepresentation getForm(@PathVariable String formId) {
        return formResource.getForm(formId);
    }

    @RequestMapping(value = "/{formId}", method = RequestMethod.PUT, produces = "application/json")
    public FormRepresentation saveForm(@PathVariable String formId, @RequestBody FormSaveRepresentation saveRepresentation) {
        return formResource.saveForm(formId,saveRepresentation);
    }

}
