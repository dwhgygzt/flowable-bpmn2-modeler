package com.middol.flowable.modeler.controller;

import org.flowable.ui.common.model.ResultListDataRepresentation;
import org.flowable.ui.modeler.rest.app.FormsResource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * form操作 controller
 *
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app/rest/form-models")
public class FormsResourceController {

    @Resource
    private FormsResource formsResource;

    @RequestMapping(method = RequestMethod.GET, produces = "application/json")
    public ResultListDataRepresentation getForms(HttpServletRequest request) {
        return formsResource.getForms(request);
    }


}
