package com.middol.flowable.modeler.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @author guzt
 */
@Data
public class UserTaskNodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    protected String id;
    protected String name;
    protected String assignee;
    protected String formKey;
    protected String category;
    protected boolean hasMultiInstance;
    protected boolean sequential;

    protected boolean inParallelGatway;

    /**
     * 如果是并行网关上的节点，该值表示是属于哪一个分支线上的节点
     */
    protected String parallelGatewayForkRef;

    protected String forkParallelGatwayId;

    protected String joinParallelGatwayId;

}
