// Large graph model for performance testing
module test/large_graph

sig Node {
    edges: set Node,
    label: Int
}

fact GraphConstraints {
    // No self-loops
    no n: Node | n in n.edges
    
    // Symmetric edges (undirected graph)
    all n1, n2: Node | n1 in n2.edges iff n2 in n1.edges
    
    // Unique labels
    all disj n1, n2: Node | n1.label != n2.label
    
    // Labels are sequential
    all n: Node | n.label >= 0 and n.label < #Node
}

pred Connected {
    // Graph is connected
    all n1, n2: Node | n1 in n2.^edges
}

pred HasCycle {
    some n: Node | n in n.^edges
}

pred CompleteGraph {
    all disj n1, n2: Node | n1 in n2.edges
}

pred BipartiteGraph {
    some disj A, B: set Node | 
        A + B = Node and
        no A & B and
        all a: A, b: B | a in b.edges and
        no disj a1, a2: A | a1 in a2.edges and
        no disj b1, b2: B | b1 in b2.edges
}

fun MaxDegree: Int {
    max n: Node | #n.edges
}

fun MinDegree: Int {
    min n: Node | #n.edges
}

assert ConnectedImpliesPath {
    Connected implies (all disj n1, n2: Node | n1 in n2.^edges)
}

// Performance test commands with increasing scope
run Connected for 5
run Connected for 10
run Connected for 15
run HasCycle for 8
run CompleteGraph for 6
run BipartiteGraph for 8
check ConnectedImpliesPath for 7

// Stress test with large scope
run Connected for 20 but 10 int
