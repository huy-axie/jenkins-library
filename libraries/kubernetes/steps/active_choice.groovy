package libraries.kubernetes

void call(){    
    List JustTest() {
         List xxx = ['a','b']
         return xxx
    }

    properties([
        parameters([
            choice(name: 'PARAM', choices: JustTest().join('\n'), description: 'Choice'),
        ])
    ])
}