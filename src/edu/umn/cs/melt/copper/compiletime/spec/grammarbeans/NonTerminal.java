package edu.umn.cs.melt.copper.compiletime.spec.grammarbeans;

import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.visitors.CopperASTBeanVisitor;

/**
 * Represents a nonterminal symbol.
 * @author August Schwerdfeger &lt;<a href="mailto:schwerdf@cs.umn.edu">schwerdf@cs.umn.edu</a>&gt;
 *
 */
public class NonTerminal extends GrammarSymbol
{
	/**
	 * 
	 */
	private static final long serialVersionUID = 127917288537652493L;

	public NonTerminal()
	{
		super(CopperElementType.NON_TERMINAL);
	}
	
	public <RT,E extends Exception> RT acceptVisitor(CopperASTBeanVisitor<RT,E> visitor)
	throws E
	{
		return visitor.visitNonTerminal(this);
	}
}
