package com.terracottatech.qa.angela.client;

import com.terracottatech.qa.angela.common.cluster.Cluster;

import java.io.Serializable;

/**
 * @author Ludovic Orban
 */
// this class has to be serializable for Ignite to be able to remote it
public interface ClientJob extends Serializable {

  void run(Cluster cluster) throws Exception;

}
