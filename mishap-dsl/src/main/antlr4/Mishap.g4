grammar Mishap;

@header {
    package io.github.aurellya.mishap.dsl;
}

// P.A.R.S.E.R

system  : 'system' ID '{' member* '}' EOF ;

member  : initDecl
        | service
        | failuresDecl
        | lossyDecl
        | boundsDecl
        | property
        ;

initDecl : 'init' ':' sendRef (',' sendRef)* ;
sendRef  : 'send' msgRef ;
msgRef   : ID '.' ID ;

service       : 'service' ID '{' serviceMember* '}' ;
serviceMember : stateDecl | initialDecl | varDecl | transition ;

stateDecl   : 'state' ':' ID (',' ID)* ;
initialDecl : 'initial' ':' ID ;
varDecl     : 'var' ID '=' INT ;

transition : 'on' trigger 'in' ID '->' action (',' action)* ;
trigger    : 'timeout' | ID ;

action : 'goto' ID      # GotoAction
       | 'send' msgRef  # SendAction
       | ID '=' expr    # AssignAction
       ;

failuresDecl : 'failures' ':' failure (',' failure)* ;
failure      : 'lose' | 'duplicate' ;

lossyDecl : 'lossy' ':' msgRef (',' msgRef)* ;

boundsDecl : 'bounds' '{' (ID ':' INT)* '}' ;

property : kind=('invariant' | 'final') ID ':' expr ;

// Precedence: top = highest
expr : expr op=('+' | '-') expr                              # AddSub
     | expr op=('==' | '!=' | '<=' | '>=' | '<' | '>') expr  # Compare
     | 'not' expr                                            # Not
     | expr 'and' expr                                       # And
     | expr 'or' expr                                        # Or
     | '(' expr ')'                                          # Parens
     | INT                                                   # IntLit
     | ref                                                   # RefExpr
     | ID                                                    # NameExpr
     ;

ref : ID '.' (ID | 'state') ;

// L.E.X.E.R

ID           : [a-zA-Z_] [a-zA-Z_0-9]* ;
INT          : [0-9]+ ;
LINE_COMMENT : '//' ~[\r\n]* -> skip ;
WS           : [ \t\r\n]+ -> skip ;