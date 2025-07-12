// File system model for testing complex scenarios
module test/file_system

abstract sig Object {}
sig File extends Object {}
sig Dir extends Object {
    contents: set Object
}

one sig Root extends Dir {}

fact FileSystemStructure {
    // No object contains itself
    no o: Object | o in o.^contents
    
    // Every object except Root has exactly one parent
    all o: Object - Root | one d: Dir | o in d.contents
    
    // Root has no parent
    no d: Dir | Root in d.contents
}

pred WellFormed {
    // File system is a tree rooted at Root
    all o: Object | o in Root.^contents or o = Root
}

pred HasFile[d: Dir, f: File] {
    f in d.contents
}

pred HasSubdir[parent: Dir, child: Dir] {
    child in parent.contents
}

fun AllFiles: set File {
    File & Root.^contents
}

fun AllDirs: set Dir {
    Dir & Root.^contents + Root
}

fun EmptyDirs: set Dir {
    {d: Dir | no d.contents}
}

assert RootReachable {
    all o: Object | o in Root.^contents or o = Root
}

assert NoCircularContainment {
    no o: Object | o in o.^contents
}

// Test commands with different scopes
run WellFormed for 3
run WellFormed for 5 but exactly 1 Root
check RootReachable for 4
check NoCircularContainment for 6

// Test with specific constraints
run {some f: File | HasFile[Root, f]} for 3
run {some d: Dir - Root | HasSubdir[Root, d]} for 4
run {some EmptyDirs} for 3
