package edu.umn.cs.melt.copper.compiletime.builders;

import edu.umn.cs.melt.copper.compiletime.logging.CompilerLevel;
import edu.umn.cs.melt.copper.compiletime.logging.CompilerLogger;
import edu.umn.cs.melt.copper.compiletime.lrdfa.LR0DFA;
import edu.umn.cs.melt.copper.compiletime.lrdfa.LR0ItemSet;
import edu.umn.cs.melt.copper.compiletime.lrdfa.LRLookaheadAndLayoutSets;
import edu.umn.cs.melt.copper.compiletime.lrdfa.TransparentPrefixes;
import edu.umn.cs.melt.copper.compiletime.parsetable.LRParseTable;
import edu.umn.cs.melt.copper.compiletime.parsetable.MutableLRParseTable;
import edu.umn.cs.melt.copper.compiletime.scannerdfa.SingleScannerDFAAnnotations;
import edu.umn.cs.melt.copper.compiletime.spec.grammarbeans.Regex;
import edu.umn.cs.melt.copper.compiletime.spec.numeric.PSSymbolTable;
import edu.umn.cs.melt.copper.compiletime.spec.numeric.ParserSpec;
import edu.umn.cs.melt.copper.compiletime.spec.numeric.PrecedenceGraph;

import java.util.*;

/**
 * @author Kevin Viratyosin
 */
public class ExtensionFragmentDataBuilder {

    private ParserSpec fullSpec;
    private LR0DFA fullDFA;
    private LRParseTable fullParseTable;
    //private PSSymbolTable fullSymbolTable;
    private LRLookaheadAndLayoutSets fullLookaheadAndLayoutSets;
    private TransparentPrefixes fullPrefixes;

    private int extensionStateCount;

    // composed to decomposed maps
    private ExtensionMappingSpec mappingSpec;

    public static ExtensionFragmentData build(ParserSpec fullSpec, LR0DFA fullDFA, LRParseTable fullParseTable, PSSymbolTable fullSymbolTable, ParserSpec hostSpec, Map<Integer, Integer> hostPartitionMap, BitSet extensionStatePartition, LRLookaheadAndLayoutSets fullLookaheadAndLayoutSets, TransparentPrefixes fullPrefixes, CompilerLogger logger) {
        ExtensionFragmentDataBuilder builder = new ExtensionFragmentDataBuilder(fullSpec, fullDFA, fullParseTable, fullSymbolTable, hostSpec, hostPartitionMap, extensionStatePartition, fullLookaheadAndLayoutSets, fullPrefixes);

        ExtensionFragmentData data = builder.build();

        if (logger.getLevel() == CompilerLevel.VERBOSE) {
            System.out.println("== BEGIN ExtensionLRParseTableBuilder ==");
            System.out.println("Indices:");
            System.out.println("  host terminals: " + builder.bitSetIndicesToString(builder.mappingSpec.hostTerminalIndices));
            System.out.println("  host nonterminals: " + builder.bitSetIndicesToString(builder.mappingSpec.hostNonterminalIndices));
            System.out.println("  host productions: " + builder.bitSetIndicesToString(builder.mappingSpec.hostProductionIndices));
            System.out.println("  extension terminals: " + builder.bitSetIndicesToString(builder.mappingSpec.extensionTerminalIndices));
            System.out.println("  extension nonterminals: " + builder.bitSetIndicesToString(builder.mappingSpec.extensionNonterminalIndices));
            System.out.println("  extension productions: " + builder.bitSetIndicesToString(builder.mappingSpec.extensionProductionIndices));

            System.out.println("Maps:");
            System.out.println("  composed to decomposed symbols:");
            builder.printPartitionMap(builder.mappingSpec.composedToDecomposedSymbols);
            System.out.println("  extension state num to composed state num:");
            builder.printPartitionMap(builder.mappingSpec.extensionToComposedStates);
            System.out.println("  composed to decomposed state map:");
            builder.printPartitionMap(builder.mappingSpec.composedToDecomposedStates);

            System.out.println("Appended Extension Parse Table");
            data.appendedExtensionTable.print();

            System.out.println("== END ExtensionLRParseTableBuilder ==");
        }

        return data;
    }

    private String bitSetIndicesToString(BitSet bitSet) {
        String ret = "";
        for (int i = bitSet.nextSetBit(0); i != -1; i = bitSet.nextSetBit(i + 1)) {
            ret += i + ", ";
        }
        return ret;
    }

    private void printPartitionMap(Map<Integer, Integer> map) {
        for (Map.Entry<Integer, Integer> entry : map.entrySet()) {
            System.out.println("    " + entry.getKey() + " -> " + entry.getValue());
        }
    }

    private ExtensionFragmentDataBuilder(
            ParserSpec fullSpec,
            LR0DFA fullDFA,
            LRParseTable fullParseTable,
            PSSymbolTable fullSymbolTable,
            ParserSpec hostSpec,
            Map<Integer, Integer> hostPartitionMap,
            BitSet extensionStatePartition,
            LRLookaheadAndLayoutSets fullLookaheadAndLayoutSets,
            TransparentPrefixes fullPrefixes
    ) {
        this.fullSpec = fullSpec;
        this.fullDFA = fullDFA;
        this.fullParseTable = fullParseTable;
        //this.fullSymbolTable = fullSymbolTable;
        this.fullLookaheadAndLayoutSets = fullLookaheadAndLayoutSets;
        this.fullPrefixes = fullPrefixes;
        this.extensionStateCount = extensionStatePartition.cardinality();

        this.mappingSpec = new ExtensionMappingSpec(fullSpec, fullSymbolTable, hostSpec, hostPartitionMap, extensionStatePartition);
    }

    private ExtensionFragmentData build() {
        ExtensionFragmentData data = new ExtensionFragmentData();

        data.extensionMappingSpec = mappingSpec;

        this.generateExtensionLookaheadAndLayout(data, mappingSpec);
        this.generateExtensionParseTables(data, mappingSpec);
        this.generateExtensionTransparentPrefixes(data, mappingSpec);
        this.generateScannerDFA(data, mappingSpec);
        this.generateScannerDFAAnnotations(data, mappingSpec);
        this.generateMarkingTerminalMetadata(data, mappingSpec);

        return data;
    }

    private void generateExtensionLookaheadAndLayout(ExtensionFragmentData data, ExtensionMappingSpec mappingSpec) {
        int maxItemCount = fullLookaheadAndLayoutSets.getMaxItemCount();
        LRLookaheadAndLayoutSets extensionSets = new LRLookaheadAndLayoutSets(extensionStateCount, maxItemCount);
        for (int extState = 0; extState < extensionStateCount; extState++) {
            int composedState = mappingSpec.extensionToComposedStates.get(extState);
            mappingSpec.translateSymbolBitSetWithTableOffset(fullLookaheadAndLayoutSets.getLayout(composedState), extensionSets.getLayout(extState));
            for (int item = 0; item < maxItemCount; item++) {
                if (fullLookaheadAndLayoutSets.getLookahead(composedState, item) != null) {
                    mappingSpec.translateSymbolBitSetWithTableOffset(fullLookaheadAndLayoutSets.getLookahead(composedState, item), extensionSets.getLookahead(extState, item));
                }
                if (fullLookaheadAndLayoutSets.getItemLASources(composedState, item) != null) {
                    mappingSpec.translateSymbolBitSetWithTableOffset(fullLookaheadAndLayoutSets.getItemLASources(composedState, item), extensionSets.getItemLASources(extState, item));
                }
            }
        }
        data.extensionLookaheadAndLayoutSets = extensionSets;
    }

    private void generateExtensionTransparentPrefixes(ExtensionFragmentData data, ExtensionMappingSpec mappingSpec) {
        LRParseTable parseTable = data.appendedExtensionTable;
        int offsetTerminalsLength = mappingSpec.offsetExtensionIndex(mappingSpec.extensionTerminalIndices.length() - 1);
        TransparentPrefixes prefixes = new TransparentPrefixes(offsetTerminalsLength, parseTable.size());

        for (Map.Entry<Integer, Integer> entry : mappingSpec.extensionToComposedStates.entrySet()) {
            int extensionState = entry.getKey();
            int composedState = entry.getValue();

            mappingSpec.translateSymbolBitSetWithTableOffset(fullPrefixes.getPrefixes(composedState), prefixes.getPrefixes(extensionState));

            for (int t = 0; t < fullSpec.terminals.length(); t++) {
                BitSet originalFollowingTerminals = fullPrefixes.getFollowingTerminals(composedState, t);
                if (originalFollowingTerminals != null) {
                    int translatedT = mappingSpec.translateAndTableOffsetComposedSymbol(t);
                    prefixes.initializePrefixMap(extensionState, translatedT);
                    mappingSpec.translateSymbolBitSetWithTableOffset(originalFollowingTerminals, prefixes.getFollowingTerminals(extensionState, translatedT));
                }
            }
        }

        data.transparentPrefixes = prefixes;
    }

    // Returns partial parse table
    // only states are extension states
    // uses all symbols
    // extension states as actions parameters are referred to in negative, e.g.
    //   parameter | referenced state
    //   0         | host 0
    //   1         | host 1
    //   -1        | extension 0
    //   -2        | extension 1
    private void generateExtensionParseTables(ExtensionFragmentData data, ExtensionMappingSpec mappingSpec) {
        //int fullSpecSymbolCount = Math.max(fullSpec.terminals.length(), fullSpec.nonterminals.length());
        int extensionStateCount = mappingSpec.composedExtensionStates.cardinality();

        int hostColumnCount = mappingSpec.extensionSymbolTableOffset;
        int extensionColumnCount = Math.max(mappingSpec.extensionTerminalIndices.length(), mappingSpec.extensionNonterminalIndices.length());

        MutableLRParseTable appendedExtensionTable = new MutableLRParseTable(extensionStateCount, hostColumnCount + extensionColumnCount);

        for (int i = 0; i < extensionStateCount; i++) {
            int composedStateNumber = mappingSpec.extensionToComposedStates.get(i);
            BitSet validLA = fullParseTable.getValidLA(composedStateNumber);
            for (int symbol = validLA.nextSetBit(0); symbol >= 0; symbol = validLA.nextSetBit(symbol + 1)) {
                int convertedSymbol = mappingSpec.translateAndTableOffsetComposedSymbol(symbol);

                byte actionType = fullParseTable.getActionType(composedStateNumber, symbol);
                int composedActionParameter = fullParseTable.getActionParameter(composedStateNumber, symbol);
                int extensionActionParameter = translateActionParameter(actionType, composedActionParameter);

                appendedExtensionTable.getValidLA(i).set(convertedSymbol);
                appendedExtensionTable.setActionType(i, convertedSymbol, actionType);
                appendedExtensionTable.setActionParameter(i, convertedSymbol, extensionActionParameter);
            }
        }

        data.appendedExtensionTable = appendedExtensionTable;
    }

    private int translateActionParameter(byte actionType, int composedActionParameter) {
        switch (actionType) {
            case LRParseTable.CONFLICT:
                return 0;
            case LRParseTable.REDUCE: // convert production
                return mappingSpec.composedToDecomposedSymbols.get(composedActionParameter);
            case LRParseTable.SHIFT: // Same as GOTO (SHIFT == GOTO)
                return mappingSpec.composedToDecomposedStates.get(composedActionParameter);
            default:
                return 0;
        }
    }

    private void generateScannerDFA(ExtensionFragmentData data, ExtensionMappingSpec mappingSpec) {
        TreeMap<Integer, Regex> regexes = new TreeMap<Integer, Regex>();
        BitSet appendedTerminals = new BitSet();
        for (int i = fullSpec.terminals.nextSetBit(0); i >= 0; i = fullSpec.terminals.nextSetBit(i + 1)) {
            Regex regex = fullSpec.t.getRegex(i);
            int oi = mappingSpec.translateAndTableOffsetComposedSymbol(i);
            appendedTerminals.set(oi);
            regexes.put(oi, regex);
        }
        int translatedEOF = mappingSpec.translateAndTableOffsetComposedSymbol(fullSpec.getEOFTerminal());
        data.scannerDFA = SingleScannerDFABuilder.build(regexes, appendedTerminals, translatedEOF);
    }

    private void generateScannerDFAAnnotations(ExtensionFragmentData data, ExtensionMappingSpec mappingSpec) {
        int appendedTerminalsLength = mappingSpec.extensionSymbolTableOffset + mappingSpec.extensionTerminalIndices.length();
        PrecedenceGraph precedenceGraph = new PrecedenceGraph(appendedTerminalsLength);
        int composedTerminalsLength = fullSpec.terminals.length();

        for (int i = 0; i < composedTerminalsLength; i++) {
            int oi = mappingSpec.translateAndTableOffsetComposedSymbol(i);
            for (int j = 0; j < composedTerminalsLength; j++) {
                if (fullSpec.t.precedences.hasEdge(i, j)) {
                    int oj = mappingSpec.translateAndTableOffsetComposedSymbol(j);
                    precedenceGraph.addEdge(oi, oj);
                }
            }
        }

        SingleScannerDFAAnnotations annotations = SingleScannerDFAAnnotationBuilder.build(precedenceGraph, data.scannerDFA);
        data.scannerDFAAnnotations = annotations;
    }

    private void generateMarkingTerminalMetadata(ExtensionFragmentData data, ExtensionMappingSpec mappingSpec) {
        Map<Integer, Integer> markingTerminalLHS = new TreeMap<Integer, Integer>();
        Map<Integer, Integer> markingTerminalStates = new TreeMap<Integer, Integer>();
        BitSet[] initNTs = new BitSet[extensionStateCount];
        Map<Integer, Map<Integer, Set<Integer>>> laSources = new TreeMap<Integer, Map<Integer, Set<Integer>>>();

        BitSet composedBridgeProductions = new BitSet();
        composedBridgeProductions.or(fullSpec.bridgeConstructs);
        composedBridgeProductions.and(fullSpec.productions);
        for (int cp = composedBridgeProductions.nextSetBit(0); cp >= 0; cp = composedBridgeProductions.nextSetBit(cp + 1)) {
            int lhs = mappingSpec.composedToDecomposedSymbols.get(fullSpec.pr.getLHS(cp)); // lhs is host NT, no more conversion needed
            int composedMarkingTerminal = fullSpec.pr.getRHSSym(cp, 0); // marking terminal is first on RHS of bridge production
            int extensionMarkingTerminal = ExtensionMappingSpec.decodeExtensionIndex(mappingSpec.composedToDecomposedSymbols.get(composedMarkingTerminal));
            markingTerminalLHS.put(extensionMarkingTerminal, lhs);
        }

        for (int state = 0; state < fullDFA.size(); state++) {
            // look for the states with items: X -> t (*) ... for bridge productions
            LR0ItemSet itemSet = fullDFA.getItemSet(state);
            for (int item = 0; item < itemSet.size(); item++) {
                if (itemSet.getPosition(item) == 1 && fullSpec.bridgeConstructs.get(itemSet.getProduction(item))) {
                    int composedMarkingTerminal = fullSpec.pr.getRHSSym(itemSet.getProduction(item), 0);
                    int extensionMarkingTerminal = ExtensionMappingSpec.decodeExtensionIndex(mappingSpec.composedToDecomposedSymbols.get(composedMarkingTerminal));
                    int extensionState = ExtensionMappingSpec.decodeExtensionIndex(mappingSpec.composedToDecomposedStates.get(state));
                    markingTerminalStates.put(extensionMarkingTerminal, extensionState);
                }
            }

            if (mappingSpec.composedExtensionStates.get(state)) {
                int extensionState = ExtensionMappingSpec.decodeExtensionIndex(mappingSpec.composedToDecomposedStates.get(state));

                // build initNTs
                initNTs[extensionState] = mappingSpec.translateSymbolBitSetWithTableOffset(fullDFA.getInitNTs(state), new BitSet());

                // build laSources
                Map<Integer, Set<Integer>> stateLASources = new TreeMap<Integer, Set<Integer>>();
                int items = fullDFA.getItemSet(state).size();
                for (int item = 0; item < items; item++) {
                    int production = fullDFA.getItemSet(state).getProduction(item);
                    if (fullSpec.pr.getRHSLength(production) == fullDFA.getItemSet(state).getPosition(item)) {
                        int decomposedProduction = mappingSpec.composedToDecomposedSymbols.get(production);
                        BitSet sources = fullLookaheadAndLayoutSets.getItemLASources(state, item);
                        for (int nt = sources.nextSetBit(0); nt >= 0; nt = sources.nextSetBit(nt + 1)) {
                            int toNT = mappingSpec.translateAndTableOffsetComposedSymbol(nt);
                            if (stateLASources.get(toNT) == null) {
                                stateLASources.put(toNT, new HashSet<Integer>());
                            }
                            stateLASources.get(toNT).add(decomposedProduction);
                        }
                    }
                }
                laSources.put(extensionState, stateLASources);
            }
        }

        Map<Integer, Regex> markingTerminalRegexes = new TreeMap<Integer, Regex>();
        BitSet composedMarkingTerminals = new BitSet();
        composedMarkingTerminals.or(fullSpec.bridgeConstructs);
        composedMarkingTerminals.and(fullSpec.terminals);
        for (int ct = composedMarkingTerminals.nextSetBit(0); ct >= 0; ct = composedMarkingTerminals.nextSetBit(ct + 1)) {
            int extensionMarkingTerminal = ExtensionMappingSpec.decodeExtensionIndex(mappingSpec.composedToDecomposedSymbols.get(ct));
            markingTerminalRegexes.put(extensionMarkingTerminal, fullSpec.t.getRegex(ct));
        }

        data.markingTerminalLHS = markingTerminalLHS;
        data.markingTerminalStates = markingTerminalStates;
        data.markingTerminalRegexes = markingTerminalRegexes;
        data.initNTs = initNTs;
        data.laSources = laSources;
    }
}
