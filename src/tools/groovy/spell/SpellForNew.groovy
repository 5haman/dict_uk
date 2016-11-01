#!/bin/env groovy

@Grab(group='org.languagetool', module='language-uk', version='3.6-SNAPSHOT', changing=true)
@Grab(group='org.languagetool', module='language-ru', version='3.5')
@Grab(group='commons-cli', module='commons-cli', version='1.3')

import org.codehaus.groovy.util.StringUtil
import org.languagetool.*
import org.languagetool.tagging.*
import org.languagetool.rules.*
import org.languagetool.tokenizers.*
import org.languagetool.language.*
import org.languagetool.tagging.ru.*
import org.apache.commons.lang.WordUtils

class SpellForNew {
	
	def LETTER = ~ /[а-щьюяіїєґА-ЩЬЮЯІЇЄҐ][а-щьюяіїєґА-ЩЬЮЯІЇЄҐ'-]*[а-щьюяіїєґА-ЩЬЮЯІЇЄҐ]/
	def IGNORE_PATTERN = ~ /[ІХ]+/
	def options
	
	Language ukLang = new Ukrainian()
	JLanguageTool langTool = new MultiThreadedJLanguageTool(ukLang)
	Tagger ukrainianTagger = ukLang.getTagger()
	RussianTagger russianTagger = new RussianTagger();
	

	def analyzeText(String text) {
        // remove words with dot as we get too much abbreviations
        text = text.replaceAll(/[а-яіїєґ]+\./, '.').replace("\u0301", "")

		List<AnalyzedSentence> analyzedSentences = langTool.analyzeText(text);
	}
	
	// check if normal word is spelled with all caps
	def isUppercaseLastname(word) {
	    if( Character.isLowerCase(word.charAt(word.length()-1)) )
	        return false

	    def posTag = ukrainianTagger.tag(WordUtils.capitalize(word.toLowerCase()))[0].getReadings().get(0).getPOSTag()
	    return posTag != null
	}
	
	def spell(analyzed) { 
	    
        analyzed.collect { AnalyzedSentence sent ->
			def tokens = sent.getTokens()
			
			
			tokens.findAll { AnalyzedTokenReadings reading ->
				AnalyzedToken token = reading.getReadings().get(0)
				token.getToken() != null && (token.getPOSTag() == null /*|| !token.getPOSTag().contains(":lname")*/) \
				    && LETTER.matcher(token.getToken()).matches() \
				    && ! IGNORE_PATTERN.matcher(token.getToken()).matches() \
				    && ! isUppercaseLastname(token.getToken()) \
				    && (! options.norus || russianTagger.tag( Arrays.asList(token.getToken()) )[0].getReadings().get(0).getPOSTag() == null)
				//				}
			}.collect { token ->
				token.getToken()
			}
	    }
	}

    def spellIt(inputFile) {
		def unknownWords = []
		
		def text = ""
		def lines = 0
		
		def textToAnalyze = inputFile.eachLine { line ->
            lines += 1
            if( lines % 1000 == 0 ) {
                println "$lines lines"
            }
            
            text += "\n" + line
            if( text.length() < 60*1024 )
                return

//            print "."
            
			def analyzed = analyzeText(text)

			unknownWords << spell(analyzed)
			
			text = ""
			analyzed = ""
		}
		
		if( text ) {
			def analyzed = analyzeText(text)
			unknownWords << spell(analyzed)
		}
		
		
		def sortedMap = unknownWords.flatten().countBy { it }.toSorted{ ev -> -ev.value }
		println "- " + sortedMap.size() + " words"

		def outputFileMain = new File(inputFile.name + ".unknown.txt")
		outputFileMain.text = ""

		def outputFileCaps = new File(inputFile.name + ".unknown.caps.txt")
		outputFileCaps.text = ""

		def outputFileAbbr = new File(inputFile.name + ".unknown.abbr.txt")
		outputFileAbbr.text = ""

		
		sortedMap.each { k,v ->
		    if( Character.isLowerCase(k.charAt(0)) ) {
    		    outputFileMain << "$k $v\n"
    		}
		    else if( Character.isUpperCase(k.charAt(1)) ) {
    		    outputFileAbbr << "$k $v\n"
    		}
		    else {
    		    outputFileCaps << "$k $v\n"
    		}
		}
    }


	static void main(String[] argv) {
		
		def cli = new CliBuilder()

		cli.i(longOpt: 'input', args:1, required: true, 'Input file')
//		cli.o(longOpt: 'output', args:1, required: true, 'Output file')
		cli.nr(longOpt: 'norus', 'Filter out Russian words')
		cli.h(longOpt: 'help', 'Help - Usage Information')


		def options = cli.parse(argv)

		if (!options) {
			System.exit(0)
		}

		if ( options.h ) {
			cli.usage()
			System.exit(0)
		}


		def nlpUk = new SpellForNew()
		nlpUk.options = options

		
		def inputFile = new File(options.input)
		
		nlpUk.spellIt(inputFile)
	}
}
