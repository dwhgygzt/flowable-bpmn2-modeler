package com.middol.flowable.modeler.controller;


import org.flowable.ui.common.model.ResultListDataRepresentation;
import org.flowable.ui.modeler.rest.app.EditorUsersResource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 用户组操作 controller
 *
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app")
public class EditorUsersResourceController {

    @Resource
    private EditorUsersResource editorUsersResource;

    @RequestMapping(value = "/rest/editor-users", method = RequestMethod.GET)
    public ResultListDataRepresentation getUsers(@RequestParam(value = "filter", required = false) String filter) {
        return editorUsersResource.getUsers(filter);
    }

}
