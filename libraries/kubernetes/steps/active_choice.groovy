package libraries.kubernetes

void call(){    
    properties([
        parameters([
            choice(name: 'PARAM', choices: JustTest().join('\n'), description: 'Choice'),
        ])
    ])
}

List JustTest() {
    List xxx = ['a','b']
    return xxx
}