// Model with valid syntax but impossible constraints that will fail checking
module test/impossible_constraints

sig Person {
    friends: set Person,
    age: Int
}

sig Student extends Person {}
sig Teacher extends Person {}

// These facts create impossible constraints
fact ImpossibleFacts {
    // Constraint 1: Everyone must be friends with everyone else
    all p1, p2: Person | p1 != p2 implies p1 in p2.friends
    
    // Constraint 2: No one can have any friends (contradicts above)
    no p: Person | some p.friends
    
    // Constraint 3: All ages must be positive
    all p: Person | p.age > 0
    
    // Constraint 4: All ages must be negative (contradicts above)
    all p: Person | p.age < 0
}

fact DisjointSets {
    // Students and Teachers are disjoint
    no Student & Teacher
    
    // But everyone must be both a Student and Teacher (impossible)
    Person = Student
    Person = Teacher
}

fact CardinalityConstraints {
    // Must have exactly 3 people
    #Person = 3
    
    // But also must have exactly 5 people (impossible)
    #Person = 5
    
    // And must have no people at all (also impossible)
    no Person
}

pred SomeValidPredicate {
    some Person
    some p: Person | p.age > 18
}

// This assertion will fail because the facts are contradictory
assert EveryoneHasFriends {
    all p: Person | some p.friends
}

// These commands will fail to find satisfying instances
run SomeValidPredicate for 3
run SomeValidPredicate for 5
check EveryoneHasFriends for 4
