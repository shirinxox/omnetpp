/*--------------------------------------------------------------*
  Copyright (C) 2006-2008 OpenSim Ltd.
  
  This file is distributed WITHOUT ANY WARRANTY. See the file
  'License' for details on this and other legal matters.
*--------------------------------------------------------------*/

package org.omnetpp.ned.model;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.Assert;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.omnetpp.common.Debug;
import org.omnetpp.common.util.StringUtils;
import org.omnetpp.ned.engine.NED1Generator;
import org.omnetpp.ned.engine.NED2Generator;
import org.omnetpp.ned.engine.NEDDTDValidator;
import org.omnetpp.ned.engine.NEDElement;
import org.omnetpp.ned.engine.NEDElementCode;
import org.omnetpp.ned.engine.NEDErrorSeverity;
import org.omnetpp.ned.engine.NEDErrorStore;
import org.omnetpp.ned.engine.NEDParser;
import org.omnetpp.ned.engine.NEDSourceRegion;
import org.omnetpp.ned.engine.NEDSyntaxValidator;
import org.omnetpp.ned.engine.NEDTools;
import org.omnetpp.ned.model.ex.MsgFileElementEx;
import org.omnetpp.ned.model.ex.NedFileElementEx;
import org.omnetpp.ned.model.pojo.NEDElementFactory;
import org.omnetpp.ned.model.pojo.NEDElementTags;
import org.omnetpp.ned.model.pojo.NedFileElement;
import org.omnetpp.ned.model.ui.NedModelContentProvider;
import org.omnetpp.ned.model.ui.NedModelLabelProvider;

/**
 * Utility functions working on NEDElement trees. Conversions, serialization, dumping of trees.
 *
 * @author rhornig, andras
 */
public class NEDTreeUtil {

	private static ITreeContentProvider nedModelContentProvider = new NedModelContentProvider();
    private static ILabelProvider nedModelLabelProvider = new NedModelLabelProvider();

    /**
	 * Generate NED code from the given NEDElement tree. The root node
	 * does not have to be NedFileElement, any subtree can be converted
	 * to source form.
	 *
	 * @param keepSyntax if set, sources parsed in old syntax (NED-1) will be generated in old syntax as well
	 */
    public static String generateNedSource(INEDElement treeRoot, boolean keepSyntax) {
		// XXX for debugging
        //Debug.println(generateXmlFromPojoElementTree(treeRoot,""));

        NEDErrorStore errors = new NEDErrorStore();
		errors.setPrintToStderr(false); // turn it on for debugging
		if (keepSyntax && treeRoot instanceof NedFileElement && "1".equals(((NedFileElement)treeRoot).getVersion())) {
			NED1Generator ng = new NED1Generator(errors);
            return ng.generate(pojo2swig(treeRoot), ""); // TODO check NEDErrorStore for conversion errors!!
		}
		else {
			NED2Generator ng = new NED2Generator(errors);
			return ng.generate(pojo2swig(treeRoot), ""); // TODO check NEDErrorStore for errors!!
		}
    }

	/**
	 * Load and parse NED file to a NEDElement tree. Returns a non-null,
	 * DTD-conforming (but possibly incomplete) tree even in case of parse errors. 
     * The passed displayFileName will only be used to fill in the NedFileElement element 
     * and source location attributes. Callers should check NEDErrorStore to determine 
     * whether a parse error occurred. All errors produced here will be syntax errors 
     * (see NEDSYNTAXPROBLEM_MARKERID).
	 */
	public static synchronized NedFileElementEx parseNedFile(String filesystemFilename, INEDErrorStore errors, String displayFilename) {
        return doParseNedSource(null, filesystemFilename, errors, displayFilename);
	}

    /**
     * Parse the NED source and return it as a NEDElement tree. Returns a non-null, 
     * DTD-conforming (but possibly incomplete) tree even in case of parse errors. 
     * The passed displayFileName will only be used to fill in the NedFileElement element 
     * and source location attributes. Callers should check INEDErrorStore to determine 
     * whether a parse error occurred. All errors produced here will be syntax errors 
     * (see NEDSYNTAXPROBLEM_MARKERID).
     */
	public static synchronized NedFileElementEx parseNedText(String source, INEDErrorStore errors, String displayFilename) {
        return doParseNedSource(source, null, errors, displayFilename);
	}

	private static synchronized NedFileElementEx doParseNedSource(String source, String filesystemFilename, INEDErrorStore errors, String displayFilename) {
		Assert.isTrue(displayFilename != null);
		NEDElement swigTree = null;
		try {
		    // Debug.println("Parsing NED file started: " + filesystemFilename);
			// parse
			NEDErrorStore swigErrors = new NEDErrorStore();
			NEDParser np = new NEDParser(swigErrors);
			np.setParseExpressions(false);
			swigTree = source!=null ? np.parseNEDText(source, displayFilename) : np.parseNEDFile(filesystemFilename, displayFilename);
			if (swigTree == null) {
				// return an empty NedFileElement if parsing totally failed
				NedFileElementEx fileNode = (NedFileElementEx)NEDElementFactory.getInstance().createElement(NEDElementTags.NED_NED_FILE, null);
				fileNode.setFilename(displayFilename);
				return fileNode;
			}

			if (!swigErrors.empty()) {
				// There were parse errors, and the tree built may not be entirely correct.
				// Typical problems are "mandatory attribute missing" especially with connections,
				// due to parse errors before filling in the connection element was completed.
				// Here we try to check and repair the tree by discarding elements that cause
				// DTD validation error.
				NEDTools.repairNEDElementTree(swigTree);
			}

			// run DTD validation (once again)
			int numMessages = swigErrors.numMessages();
			NEDDTDValidator dtdvalidator = new NEDDTDValidator(swigErrors);
			dtdvalidator.validate(swigTree);
			dumpSwigErrors(swigErrors); //XXX remove -- debugging only
			
			Assert.isTrue(swigErrors.numMessages() == numMessages, "NED tree fails DTD validation, even after repairs");

			// additional syntax-related validation
			NEDSyntaxValidator syntaxValidator = new NEDSyntaxValidator(false, swigErrors);
			syntaxValidator.validate(swigTree);

			// convert tree to pure Java objects
			INEDElement pojoTree = swig2pojo(swigTree, null, swigErrors, errors);
			Assert.isTrue(swigErrors.numMessages() == errors.getNumProblems(), "problems lost in translation");

			// Debug.println(generateXmlFromPojoElementTree(pojoTree, ""));

			return (NedFileElementEx)pojoTree;
		}
		finally {
			if (swigTree != null)
				swigTree.delete();

			// Debug.println("Parsing NED file finished: " + filesystemFilename);
		}
	}

    public static synchronized MsgFileElementEx parseMsgSource(String source, INEDErrorStore errors, String filename) {
        Assert.isTrue(filename != null);
        NEDElement swigTree = null;
        try {
            // Debug.println("Parsing MSG file started: " + filename);
            // parse
            NEDErrorStore swigErrors = new NEDErrorStore();
            NEDParser np = new NEDParser(swigErrors);
            np.setParseExpressions(false);
            swigTree = source!=null ? np.parseMSGText(source, filename) : np.parseMSGFile(filename);
            if (swigTree == null) {
                // return an empty MsgFileElement if parsing totally failed
                MsgFileElementEx fileNode = (MsgFileElementEx)NEDElementFactory.getInstance().createElement(NEDElementTags.NED_MSG_FILE, null);
                fileNode.setFilename(filename);
                return fileNode;
            }

            // set the file name property in the nedFileElement
            if (NEDElementCode.swigToEnum(swigTree.getTagCode()) == NEDElementCode.NED_MSG_FILE)
                swigTree.setAttribute("filename", filename);

            if (!swigErrors.empty()) {
                // There were parse errors, and the tree built may not be entirely correct.
                // Typical problems are "mandatory attribute missing" especially with connections,
                // due to parse errors before filling in the connection element was completed.
                // Here we try to check and repair the tree by discarding elements that cause
                // DTD validation error.
                NEDTools.repairNEDElementTree(swigTree);
            }

            // run DTD validation (once again)
            int numMessages = swigErrors.numMessages();
            NEDDTDValidator dtdvalidator = new NEDDTDValidator(swigErrors);
            dtdvalidator.validate(swigTree);
            dumpSwigErrors(swigErrors); //XXX remove -- debugging only
            Assert.isTrue(swigErrors.numMessages() == numMessages, "NED tree fails DTD validation, even after repairs");

            // additional syntax-related validation
            NEDSyntaxValidator syntaxValidator = new NEDSyntaxValidator(false, swigErrors);
            syntaxValidator.validate(swigTree);

            // convert tree to pure Java objects
            INEDElement pojoTree = swig2pojo(swigTree, null, swigErrors, errors);
            Assert.isTrue(swigErrors.numMessages() == errors.getNumProblems(), "problems lost in translation");

            // Debug.println(generateXmlFromPojoElementTree(pojoTree, ""));

            return (MsgFileElementEx)pojoTree;
        }
        finally {
            if (swigTree != null)
                swigTree.delete();

            // Debug.println("Parsing MSG file finished: " + filename);
        }
    }

    protected static void dumpSwigErrors(NEDErrorStore swigErrors) {
        int n = swigErrors.numMessages();
        if (n > 0) {
            Debug.println(n + " errors:");
            for (int i=0 ; i<n ; i++) {
                NEDElement context = swigErrors.errorContext(i);
                Debug.println(
                        swigErrors.errorText(i)+
                        " loc: "+swigErrors.errorLocation(i) +
                        " context: " + (context==null ? "" : "<"+context.getTagName()+"> at "+context.getSourceLocation()));
                if (context!=null && context.getParent()!=null)
                    Debug.println(generateXmlFromSwigElementTree(context.getParent(), "  "));
                else if (context!=null)
                    Debug.println(generateXmlFromSwigElementTree(context, "  "));
            }
        }
    }
	
	/**
	 * Converts a native C++ (SWIG-wrapped) NEDElement tree to a plain java tree.
	 * NOTE: There are two different NEDElement types handled in this function.
	 */
	public static INEDElement swig2pojo(NEDElement swigNode, INEDElement parent, NEDErrorStore swigErrors, INEDErrorStore errors) {
		// convert tree
		INEDElement pojoTree = doSwig2pojo(swigNode, parent, swigErrors, errors);

		// piggyback errors which came without context node onto the tree root
		if (swigErrors.findFirstErrorFor(null, 0) != -1) {
			int i = -1;
			while ((i = swigErrors.findFirstErrorFor(null, i+1)) != -1) {
				int severity = getMarkerSeverityFor(NEDErrorSeverity.swigToEnum(swigErrors.errorSeverityCode(i)));
				errors.add(severity, pojoTree, getLineFrom(swigErrors.errorLocation(i)), swigErrors.errorText(i));
			}
		}
		
		return pojoTree;
	}
	
	protected static INEDElement doSwig2pojo(NEDElement swigNode, INEDElement parent, NEDErrorStore swigErrors, INEDErrorStore errors) {
		INEDElement pojoNode = NEDElementFactory.getInstance().createElement(swigNode.getTagCode(), parent);

		// set the attributes
		for (int i = 0; i < swigNode.getNumAttributes(); ++i)
			pojoNode.setAttribute(i, swigNode.getAttribute(i));

		// copy source line number info
		pojoNode.setSourceLocation(swigNode.getSourceLocation());
		NEDSourceRegion swigRegion = swigNode.getSourceRegion();
		if (swigRegion.getStartLine() != 0)
			pojoNode.setSourceRegion(new org.omnetpp.ned.model.NEDSourceRegion(
					swigRegion.getStartLine(), swigRegion.getStartColumn(),
					swigRegion.getEndLine(), swigRegion.getEndColumn()));
		
		// take over error messages related to this node
		if (swigErrors.findFirstErrorFor(swigNode, 0) != -1) {
			int i = -1;
			while ((i = swigErrors.findFirstErrorFor(swigNode, i+1)) != -1) {
				int severity = getMarkerSeverityFor(NEDErrorSeverity.swigToEnum(swigErrors.errorSeverityCode(i)));
				errors.add(severity, pojoNode, getLineFrom(swigErrors.errorLocation(i)), swigErrors.errorText(i));
				//FIXME handle errors where context==NULL too! find closest INEDElement for them!
			}
		}

		// create child nodes
		for (NEDElement child = swigNode.getFirstChild(); child != null; child = child.getNextSibling())
			doSwig2pojo(child, pojoNode, swigErrors, errors);

		return pojoNode;
	}

	public static int getMarkerSeverityFor(NEDErrorSeverity severity) {
		switch (severity) {
		    case NED_SEVERITY_ERROR: return IMarker.SEVERITY_ERROR;
		    case NED_SEVERITY_WARNING: return IMarker.SEVERITY_WARNING;
		    case NED_SEVERITY_INFO: return IMarker.SEVERITY_INFO;
		    default: throw new IllegalArgumentException();
		}
	}

	/**
	 * Get line number from a string in the format "file:line"
	 */
	public static int getLineFrom(String location) {
		String lineStr = StringUtils.substringAfterLast(location, ":");
        int line = 1;
        try {line = Integer.parseInt(lineStr);} catch (Exception e) {}
        return line;
	}

	/**
	 * Converts a plain java NEDElement tree to a native C++ (SWIG-wrapped) tree.
	 * NOTE: There are two different NEDElement types handled in this function.
	 */
	public static NEDElement pojo2swig(INEDElement pojoNode) {

		NEDElement swigNode = org.omnetpp.ned.engine.NEDElementFactory.getInstance()
				.createElementWithTag(pojoNode.getTagCode());

		// set the attributes
		for (int i = 0; i < pojoNode.getNumAttributes(); ++i) {
			String value = pojoNode.getAttribute(i);
			value = value == null ? "" : value;
			swigNode.setAttribute(i, value);
		}
		swigNode.setSourceLocation(pojoNode.getSourceLocation());

		// create child nodes
		for (INEDElement child = pojoNode.getFirstChild(); child != null; child = child.getNextSibling()) {
            NEDElement convertedChild = pojo2swig(child);
			if (convertedChild != null)
                swigNode.appendChild(convertedChild);
		}

		return swigNode;
	}

	/**
	 * Returns true if the given string can be parsed as a NED expression, and false otherwise.
	 */
	public static boolean isExpressionValid(String expression) {
		Assert.isTrue(expression != null);
		NEDErrorStore errors = new NEDErrorStore();
		NEDParser np = new NEDParser(errors);
		NEDElement swigTree = np.parseNEDExpression(expression);
		if (swigTree != null) swigTree.delete();
		return !errors.containsError();
	}

	/**
	 * Converts a NEDElement tree to an XML-like textual format. Useful for debugging.
	 */
	public static String generateXmlFromSwigElementTree(NEDElement swigNode, String indent) {
		String result = indent;
		result += "<" + swigNode.getTagName();
		for (int i = 0; i < swigNode.getNumAttributes(); ++i)
			result += " " + swigNode.getAttributeName(i) + "=\""
					+ swigNode.getAttribute(i) + "\"";
		if (swigNode.getFirstChild() == null) {
			result += "/> \n";
		}
		else {
			result += "> \n";
			for (NEDElement child = swigNode.getFirstChild(); child != null; child = child
					.getNextSibling())
				result += generateXmlFromSwigElementTree(child, indent + "  ");

			result += indent + "</" + swigNode.getTagName() + ">\n";
		}
		return result;
	}

	public static String generateXmlFromPojoElementTree(INEDElement pojoNode, String indent, boolean printDebugString) {
		String result = indent;
		result += "<" + pojoNode.getTagName();
		for (int i = 0; i < pojoNode.getNumAttributes(); ++i)
			result += " " + pojoNode.getAttributeName(i) + "=\""
					+ pojoNode.getAttribute(i) + "\"";

        String debugString = !printDebugString ? "" : pojoNode.debugString();
        if (!"".equals(debugString))
                debugString = "<!-- "+debugString + " -->";

		if (pojoNode.getFirstChild() == null) {
			result += "/> " +  debugString + "\n";
		}
		else {
			result += "> " +  debugString + "\n";
			for (INEDElement child = pojoNode.getFirstChild(); child != null; child = child.getNextSibling())
				result += generateXmlFromPojoElementTree(child, indent + "  ", printDebugString);

			result += indent + "</" + pojoNode.getTagName() + ">\n";
		}
		return result;
	}

    /**
     * Returns true if the two trees generate the same NED source code.
     * If either parameter is null, it returns false.
     */
    public static boolean isNEDTreeEqual(INEDElement tree1, INEDElement tree2) {
        if (tree1 == tree2)
            return true;
        if (tree1==null || tree2==null)
            return false;
        return tree1.getNEDSource().equals(tree2.getNEDSource());
    }

    /**
     * Returns the default content provider for ned model trees
     */
    public static ITreeContentProvider getNedModelContentProvider() {
        return nedModelContentProvider;
    }

    /**
     * Returns the default label/icon provider for ned model trees
     */
    public static ILabelProvider getNedModelLabelProvider() {
        return nedModelLabelProvider;
    }

}