// Graph model with valid syntax but unsatisfiable constraints
module test/unsatisfiable_graph

sig Node {
    edges: set Node,
    color: Int
}

fact GraphStructure {
    // Graph must be connected
    all n1, n2: Node | n1 in n2.^edges
    
    // But also must have no edges (impossible for connected graph with >1 node)
    no edges
    
    // Must have at least 2 nodes
    #Node >= 2
}

fact ColoringConstraints {
    // All nodes must have different colors
    all disj n1, n2: Node | n1.color != n2.color
    
    // But all nodes must have the same color (contradicts above)
    all n1, n2: Node | n1.color = n2.color
    
    // Colors must be in range 1-3
    all n: Node | n.color >= 1 and n.color <= 3
    
    // Must have exactly 5 nodes (impossible to color with 3 colors if all different)
    #Node = 5
}

fact CycleConstraints {
    // Graph must be acyclic (no cycles)
    no n: Node | n in n.^edges
    
    // But every node must reach itself (requires cycles)
    all n: Node | n in n.^edges
}

pred ValidGraph {
    some Node
    some edges
}

// This will be unsatisfiable due to contradictory facts
run ValidGraph for 3
run ValidGraph for 5
run ValidGraph for 8
