package com.unclebike.meshride.data

import com.unclebike.meshride.ble.MeshtasticConnection
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NodeRepository @Inject constructor(
    private val connection: MeshtasticConnection,
) {
    val nodes: StateFlow<Map<Long, MeshNode>> = connection.nodes

    val nodeCount = connection.nodes.map { it.size }

    fun getNode(nodeId: Long): MeshNode? = connection.nodes.value[nodeId]
}
