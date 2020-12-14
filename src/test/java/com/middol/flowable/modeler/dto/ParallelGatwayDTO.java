package com.middol.flowable.modeler.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * @author guzt
 */
@Data
public class ParallelGatwayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private int forkSize;

    private String forkId;

    private String joinId;

    /**
     * 该值表示当前查询的哪一个分支线上
     */
    protected String tmpForkRef;

    /**
     * 并行网关节点上的用户任务
     */
    private LinkedHashMap<String, UserTaskNodeDTO> userTasks = new LinkedHashMap<>(4);

    /**
     * 子 并行网关节点
     */
    private List<ParallelGatwayDTO> childParallelGatways = new ArrayList<>(2);

    /**
     * 父 并行网关节点
     */
    private ParallelGatwayDTO parentParallelGatwayDTO;


}
