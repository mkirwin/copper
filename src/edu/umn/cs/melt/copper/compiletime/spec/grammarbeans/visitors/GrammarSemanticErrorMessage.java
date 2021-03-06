package edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.visitors;

import edu.umn.cs.melt.copper.compiletime.logging.CompilerLevel;
import edu.umn.cs.melt.copper.compiletime.logging.CompilerLocatedLogMessage;
import edu.umn.cs.melt.copper.compiletime.logging.CompilerLogMessageType;
import edu.umn.cs.melt.copper.runtime.io.Location;

public class GrammarSemanticErrorMessage implements CompilerLocatedLogMessage<Location>
{
	public static CompilerLevel getGrammarSemanticErrorMessageLevel()
	{
		return CompilerLevel.QUIET;
	}
	
	private GrammarError errorMessage;
	
	public GrammarSemanticErrorMessage(GrammarError errorMessage)
	{
		this.errorMessage = errorMessage;
	}

	@Override
	public CompilerLevel getLevel()
	{
		return CompilerLevel.QUIET;
	}

	@Override
	public int getType()
	{
		return CompilerLogMessageType.GRAMMAR_SEMANTIC_ERROR;
	}

	@Override
	public boolean isError()
	{
		return true;
	}

	@Override
	public boolean isFatalError()
	{
		return false;
	}

	@Override
	public Location getLocation()
	{
		return errorMessage.getLocation();
	}
	
	public String toString()
	{
		return errorMessage.toString();
	}
}
