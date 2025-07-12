// Temporal model with valid syntax but paradoxical constraints
module test/temporal_paradox

sig State {
    next: lone State,
    value: Int
}

fact TemporalConstraints {
    // Must have a linear sequence of states
    all s: State | lone s.next
    
    // But also must have cycles (every state reaches itself)
    all s: State | s in s.^next
    
    // And must be acyclic (no state reaches itself)
    no s: State | s in s.^next
    
    // Must have exactly 3 states
    #State = 3
}

fact ValueConstraints {
    // Values must increase along the sequence
    all s: State | some s.next implies s.next.value > s.value
    
    // But values must also decrease along the sequence
    all s: State | some s.next implies s.next.value < s.value
    
    // All values must be the same
    all s1, s2: State | s1.value = s2.value
    
    // Values must be in range 1-10
    all s: State | s.value >= 1 and s.value <= 10
}

fact StructuralParadox {
    // Must have a unique initial state (no incoming transitions)
    one s: State | no s.~next
    
    // But every state must have exactly one predecessor
    all s: State | one s.~next
    
    // Must have a unique final state (no outgoing transitions)
    one s: State | no s.next
    
    // But every state must have exactly one successor
    all s: State | one s.next
}

sig Process {
    states: set State,
    current: one State
}

fact ProcessConstraints {
    // Current state must be in the process's states
    all p: Process | p.current in p.states
    
    // But current state must not be in the process's states
    all p: Process | p.current not in p.states
    
    // Process must own all states
    all s: State | some p: Process | s in p.states
    
    // But no process can own any states
    all p: Process | no p.states
    
    // Must have exactly one process
    one Process
}

pred ValidProcess {
    some Process
    some State
}

// These commands will fail due to the paradoxical constraints
run ValidProcess for 3
run ValidProcess for 5
run ValidProcess for 4 but exactly 3 State
