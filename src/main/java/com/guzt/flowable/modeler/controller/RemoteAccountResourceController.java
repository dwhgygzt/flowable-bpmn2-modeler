package com.guzt.flowable.modeler.controller;


import com.guzt.flowable.modeler.service.MyCurrentUserService;
import org.flowable.ui.common.model.UserRepresentation;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author <a href="mailto:Ronaldo@middol.com">Ronaldo</a>
 */
@RestController
@RequestMapping("/modeler/app")
public class RemoteAccountResourceController {


    @Resource
    private MyCurrentUserService myCurrentUserService;


    /**
     * 原先是调用 RemoteAccountResource中的getAccount方法 ;
     * remoteAccountResource.getAccount();
     *
     * @return UserRepresentation
     */
    @RequestMapping(value = "/rest/account", method = RequestMethod.GET, produces = "application/json")
    public UserRepresentation account(HttpServletRequest request, HttpServletResponse response) {
        return myCurrentUserService.initLoginUser(request, response);
    }

}
