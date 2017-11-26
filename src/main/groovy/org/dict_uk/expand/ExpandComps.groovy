#!/usr/bin/env groovy

package org.dict_uk.expand


import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import java.util.regex.*

import org.dict_uk.common.DicEntry
import org.dict_uk.expand.Expand
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class ExpandComps {
	private static final Logger log = LoggerFactory.getLogger(ExpandComps.class);

	private static final Pattern tags_re = Pattern.compile("(.*:g)[mfnodx123][sp]:v_...(.*)")
	private static final Pattern gen_vidm_pattern = Pattern.compile(":g(..:v_...(:r(in)?anim)?)")
	final Expand expand


	public ExpandComps(Expand expand) {
		this.expand = expand
	}

	@CompileStatic
	List<DicEntry> matchComps(List<DicEntry> lefts, List<DicEntry> rights, String vMisCheck) {
		
		List<DicEntry> outs = []
		Map<String, List<String>> left_v = [:]
		String left_gen = ""
		boolean mixed_gen = false

		def left_wn
		def left_tags


		for(DicEntry ln in lefts) {
//			def parts = ln.split(" ")
			def rrr = gen_vidm_pattern.matcher(ln.tagStr)
			if( ! rrr.find() ) {
				log.warn("ignoring left {}", ln)
				continue
			}

			def vidm = rrr.group(1)
			if( vidm =~ /^[mfn]s/ ) {
				if( ! left_gen ) {
					left_gen = vidm[0..1]
				} 
				else if( left_gen != vidm[0..1] ) {
					mixed_gen = true
				}
			}
			if( ! (vidm in left_v) )
				left_v[vidm] = []

			left_v[vidm].add(ln.word)
			left_wn = ln.lemma
			left_tags = ln.tagStr
		}

		if( mixed_gen ) {
			left_gen = ""
		}

		if( rights.size() == 1 ) {
			return lefts.collect { DicEntry left ->
				new DicEntry(left.word+"-"+rights[0].word, left.lemma+"-"+rights[0].word, left.tagStr) 
			}
		}
		
		for(DicEntry rn in rights) {
			def rrr = gen_vidm_pattern.matcher(rn.tagStr)
			if( ! rrr.find() ) {
				log.warn("composite: ignoring right {}", rn)
				continue
			}

			def vidm = rrr.group(1)
			if( left_gen != "" && vidm =~ /^[mfn]s/ ) {
				vidm = left_gen + vidm[2..-1]
			}

			if( !(vidm in left_v) )
				continue

			for(String left_wi in left_v[vidm]) {
				String w_infl = left_wi + "-" + rn.word
				String lemma = left_wn + "-" + rn.lemma

				if( vMisCheck && vidm =~ /[nm]s:v_mis/ && ! isMascVMisMatch(left_wi, rn.word, vMisCheck) )
					continue
	
				String tagStr = tags_re.matcher(left_tags).replaceAll('$1'+vidm+'$2')
				DicEntry entry = new DicEntry(w_infl, lemma, tagStr)
				outs.add(entry)
			}
		}

		return outs
	}

	private static boolean isMascVMisMatch(left, right, vMisCheck) {
		if( vMisCheck.startsWith("u-") && left =~ /[ую]$/ && ! (right =~ /[ую]$/) )
			return false
		if( vMisCheck.endsWith("-u") && right =~ /[ую]$/ && ! (left =~ /[ую]$/) )
			return false
//		if( left =~ /[еєо]ві$/ && ! (right =~ /([ії]|[еєо]ві)$/) )
//			return false
//		if( left =~ /[ії]$/ && ! (right =~ /([ії]|[еєо]ві)$/) )
//			return false
		return true
	}
	
	@TypeChecked
	List<DicEntry> expand_composite_line(String line) {
		if( ! line.contains(" - ") )
//			return [line]
			throw new IllegalArgumentException("Only composite lines are supported here, was: " + line)

		def parts_all
		if( line.contains(" :") ) {
			parts_all = line.split(" :")
			line = parts_all[0]
			parts_all[1] = ":" + parts_all[1]
		}
		else if( line.contains(" ^") ) {
			parts_all = line.split(/ \^/)
			line = parts_all[0]
			parts_all[1] = "^" + parts_all[1]
		}
		else {
			parts_all = [line]
		}

		String[] parts = line.split(" - ")

		if( parts_all.size() > 1 ) {
			def extra_tags = parts_all[1]
			parts[0] += " " + extra_tags
			
			if( parts[1].contains("/") ) {
				parts[1] += " " + extra_tags
			}
		}

		List<DicEntry> lefts = expand.expand_line(parts[0]) //, true)

		List<DicEntry> rights = parts[1].contains("/") ? expand.expand_line(parts[1]) : [new DicEntry(parts[1], parts[1], null)]

		Matcher vmisCheckMatch = line =~ ('!([u*]-[*u])')
		String vmisCheck = ""
		if( vmisCheckMatch ) {
			vmisCheck = vmisCheckMatch.group(1)
		}
		else if( line =~ ' /adj.* /adj') {
			vmisCheck = "u-u"
		}
		return matchComps(lefts, rights, vmisCheck)
	}
	
	@TypeChecked
	def process_input(List<String> lines) {
		List<DicEntry> out = []

		for(String line in lines) {

            line = line.replaceFirst(/ *#.*$/, '')

			line = line.trim()
			if( ! line || line[0] == "#" )
				continue

			try {
				List<DicEntry> comps = expand_composite_line(line)
				
				if( Character.isUpperCase(line.charAt(0)) && ! line.contains("<") ) {
				    comps.each { it.tagStr += ":prop" }
				}
				
				out.addAll(comps)
			}
			catch(e) {
				log.error("Failed composite at {}: {}\n", line, e.getMessage())
				throw e
			}
		}

		return out
	}

}
