// Model with intentional type errors for testing error handling
module test/type_error

sig Person {
    name: String,
    friends: set Person,
    address: Address  // Address not defined - type error
}

sig Company {
    employees: set Person
}

fact TypeErrors {
    // Type mismatch - trying to use Person where Int expected
    all p: Person | p.friends = 5  // Type error
    
    // Undefined signature reference
    all c: Company | some c.managers  // managers field not defined
    
    // Wrong multiplicity
    all p: Person | one p.friends.name  // friends is a set, can't use 'one'
}

pred BadPredicate[x: UndefinedSig] {  // UndefinedSig not defined - type error
    some x
}

run BadPredicate for 3
