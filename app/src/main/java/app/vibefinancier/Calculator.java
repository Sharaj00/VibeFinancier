package app.vibefinancier;

import java.math.*;

/** Rational arithmetic avoids intermediate rounding before the currency-scale result. */
public final class Calculator {
    private static final class Rational {
        final BigInteger n,d;
        Rational(BigInteger n,BigInteger d){if(d.signum()==0)throw new IllegalArgumentException("Cannot divide by zero.");if(d.signum()<0){n=n.negate();d=d.negate();}BigInteger gcd=n.gcd(d);this.n=n.divide(gcd);this.d=d.divide(gcd);if(this.n.bitLength()>4096||this.d.bitLength()>4096)throw new IllegalArgumentException("Result is too large.");}
        static Rational decimal(String text){try{BigDecimal x=new BigDecimal(text);return new Rational(x.unscaledValue(),BigInteger.TEN.pow(x.scale()));}catch(NumberFormatException e){throw new IllegalArgumentException("Check the decimal separator.");}}
        Rational add(Rational x){return new Rational(n.multiply(x.d).add(x.n.multiply(d)),d.multiply(x.d));}
        Rational neg(){return new Rational(n.negate(),d);}
        Rational mul(Rational x){return new Rational(n.multiply(x.n),d.multiply(x.d));}
        Rational div(Rational x){return new Rational(n.multiply(x.d),d.multiply(x.n));}
    }
    private final String text;private int index;
    private Calculator(String text){this.text=text.replace('×','*').replace('÷','/').replace('−','-').replace(',','.').replace(" ","");if(this.text.length()>256)throw new IllegalArgumentException("Expression is too long.");}
    public static String evaluate(String expression,int scale){Calculator p=new Calculator(expression);Rational result=p.sum();if(p.index!=p.text.length())throw new IllegalArgumentException("Check the expression.");return Money.plain(new BigDecimal(result.n).divide(new BigDecimal(result.d),scale,RoundingMode.HALF_EVEN));}
    private Rational sum(){Rational value=product();while(index<text.length()){char op=text.charAt(index);if(op!='+'&&op!='-')break;index++;Rational next=product();value=value.add(op=='+'?next:next.neg());}return value;}
    private Rational product(){Rational value=factor();while(index<text.length()){char op=text.charAt(index);if(op!='*'&&op!='/')break;index++;Rational next=factor();value=op=='*'?value.mul(next):value.div(next);}return value;}
    private Rational factor(){if(index>=text.length())throw new IllegalArgumentException("Finish the expression.");char c=text.charAt(index);if(c=='+'||c=='-'){index++;Rational value=factor();return c=='-'?value.neg():value;}Rational value;
        if(c=='('){index++;value=sum();if(index>=text.length()||text.charAt(index++)!=')')throw new IllegalArgumentException("Close the parenthesis.");}
        else{int start=index;while(index<text.length()&&(Character.isDigit(text.charAt(index))||text.charAt(index)=='.'))index++;if(start==index)throw new IllegalArgumentException("Enter a number.");value=Rational.decimal(text.substring(start,index));}
        while(index<text.length()&&text.charAt(index)=='%'){index++;value=value.div(Rational.decimal("100"));}return value;
    }
}
