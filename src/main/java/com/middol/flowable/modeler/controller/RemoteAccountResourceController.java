package com.middol.flowable.modeler.controller;


import com.alibaba.fastjson.JSONObject;
import org.flowable.ui.common.model.UserRepresentation;
import org.flowable.ui.common.rest.idm.remote.RemoteAccountResource;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app")
public class RemoteAccountResourceController {

    @Resource
    private RemoteAccountResource remoteAccountResource;


    /**
     * @return UserRepresentation
     * @ Autowired
     * RemoteAccountResource ;
     * remoteAccountResource.getAccount();
     */
    @RequestMapping(value = "/rest/account", method = RequestMethod.GET, produces = "application/json")
    public UserRepresentation getAccount() {
        //remoteAccountResource.getAccount();
        return JSONObject.parseObject("{\"id\":\"admin\",\"firstName\":\"Test\",\"lastName\":\"Administrator\",\"email\":\"admin@flowable.org\",\"fullName\":\"Test Administrator\",\"tenantId\":null,\"groups\":[],\"privileges\":[\"access-idm\",\"access-admin\",\"access-modeler\",\"access-task\",\"access-rest-api\"]}",
                UserRepresentation.class);
    }
}
