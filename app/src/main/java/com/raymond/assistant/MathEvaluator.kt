package com.raymond.assistant
class MathEvaluator(private val input:String) {
 private var i=0
 fun evaluate():Double { val v=expression(); spaces(); require(i==input.length){"Unexpected character in calculation."}; require(v.isFinite()){"Calculation is outside the supported range."}; return v }
 private fun expression():Double { var v=term(); while(true){spaces(); v=when{take('+')->v+term();take('-')->v-term();else->return v} } }
 private fun term():Double { var v=factor(); while(true){spaces(); v=when{take('*')||take('×')->v*factor();take('/')||take('÷')->{val d=factor();require(d!=0.0){"You cannot divide by zero."};v/d};else->return v} } }
 private fun factor():Double { spaces(); if(take('+'))return factor();if(take('-'))return -factor();if(take('(')){val v=expression();spaces();require(take(')')){"Missing closing bracket."};return v};val start=i;while(i<input.length&&(input[i].isDigit()||input[i]=='.'))i++;require(i>start){"I couldn't understand that calculation."};return input.substring(start,i).toDouble() }
 private fun take(c:Char):Boolean {if(i<input.length&&input[i]==c){i++;return true};return false}
 private fun spaces(){while(i<input.length&&input[i].isWhitespace())i++}
}