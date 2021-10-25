package com.guzt.flowable.modeler.controller;


import org.flowable.ui.common.model.GroupRepresentation;
import org.flowable.ui.common.model.ResultListDataRepresentation;
import org.flowable.ui.modeler.rest.app.EditorGroupsResource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * 用户组操作 controller
 *
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app")
public class EditorGroupsResourceController {

    @Resource
    private EditorGroupsResource editorGroupsResource;

    @RequestMapping(value = "/rest/editor-groups", method = RequestMethod.GET)
    public ResultListDataRepresentation getGroups(@RequestParam(required = false, value = "filter") String filter) {
        List<GroupRepresentation> result = (List<GroupRepresentation>) editorGroupsResource.getGroups(filter).getData();
        return new ResultListDataRepresentation(result);
    }

}
