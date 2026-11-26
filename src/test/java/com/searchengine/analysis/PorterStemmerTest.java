package com.searchengine.analysis;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Examples from Porter's 1980 paper and the reference voc.txt/output.txt pairs. */
class PorterStemmerTest {

    @ParameterizedTest
    @CsvSource({
            "caresses,caress", "ponies,poni", "ties,ti", "caress,caress", "cats,cat",
            "feed,feed", "agreed,agre", "plastered,plaster", "bled,bled", "motoring,motor", "sing,sing",
            "conflated,conflat", "troubled,troubl", "sized,size", "hopping,hop", "tanned,tan",
            "falling,fall", "hissing,hiss", "fizzed,fizz", "failing,fail", "filing,file",
            "happy,happi", "sky,sky",
            "relational,relat", "conditional,condit", "rational,ration", "valenci,valenc",
            "hesitanci,hesit", "digitizer,digit", "conformabli,conform", "radicalli,radic",
            "differentli,differ", "vileli,vile", "analogousli,analog", "vietnamization,vietnam",
            "predication,predic", "operator,oper", "feudalism,feudal", "decisiveness,decis",
            "hopefulness,hope", "callousness,callous", "formaliti,formal", "sensitiviti,sensit",
            "sensibiliti,sensibl",
            "triplicate,triplic", "formative,form", "formalize,formal", "electriciti,electr",
            "electrical,electr", "hopeful,hope", "goodness,good",
            "revival,reviv", "allowance,allow", "inference,infer", "airliner,airlin",
            "gyroscopic,gyroscop", "adjustable,adjust", "defensible,defens", "irritant,irrit",
            "replacement,replac", "adjustment,adjust", "dependent,depend", "adoption,adopt",
            "homologou,homolog", "communism,commun", "activate,activ", "angulariti,angular",
            "homologous,homolog", "effective,effect", "bowdlerize,bowdler",
            "probate,probat", "rate,rate", "cease,ceas", "controll,control", "roll,roll",
            "generalizations,gener", "running,run", "engines,engin", "search,search", "retrieval,retriev"
    })
    void stemsLikeTheReferenceImplementation(String word, String expected) {
        assertEquals(expected, new PorterStemmer().stem(word));
    }
}
