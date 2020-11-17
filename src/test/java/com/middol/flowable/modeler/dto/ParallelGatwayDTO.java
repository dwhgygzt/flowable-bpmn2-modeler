package com.middol.flowable.modeler.dto;

import lombok.Data;
import org.flowable.bpmn.model.UserTask;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @author guzt
 */
@Data
public class ParallelGatwayDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String forkId;

    private String joinId;

    private List<UserTask> userTasks = new ArrayList<>(2);

    private List<ParallelGatwayDTO> childParallelGatways = new ArrayList<>(2);


}
