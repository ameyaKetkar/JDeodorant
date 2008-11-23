package gr.uom.java.ast.decomposition.cfg;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.PostfixExpression;
import org.eclipse.jdt.core.dom.PrefixExpression;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.ThisExpression;
import org.eclipse.jdt.core.dom.VariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import gr.uom.java.ast.ASTReader;
import gr.uom.java.ast.ClassObject;
import gr.uom.java.ast.FieldInstructionObject;
import gr.uom.java.ast.FieldObject;
import gr.uom.java.ast.LocalVariableDeclarationObject;
import gr.uom.java.ast.LocalVariableInstructionObject;
import gr.uom.java.ast.MethodInvocationObject;
import gr.uom.java.ast.MethodObject;
import gr.uom.java.ast.ParameterObject;
import gr.uom.java.ast.SystemObject;
import gr.uom.java.ast.decomposition.StatementObject;

public class PDGStatementNode extends PDGNode {
	
	public PDGStatementNode(CFGNode cfgNode, Set<VariableDeclaration> variableDeclarationsInMethod) {
		super(cfgNode);
		determineDefinedAndUsedVariables(variableDeclarationsInMethod);
	}

	private void determineDefinedAndUsedVariables(Set<VariableDeclaration> variableDeclarationsInMethod) {
		CFGNode cfgNode = getCFGNode();
		if(cfgNode.getStatement() instanceof StatementObject) {
			StatementObject statement = (StatementObject)cfgNode.getStatement();
			List<LocalVariableDeclarationObject> variableDeclarations = statement.getLocalVariableDeclarations();
			for(LocalVariableDeclarationObject variableDeclaration : variableDeclarations) {
				Variable variable = new Variable(variableDeclaration.getVariableDeclaration());
				declaredVariables.add(variable);
				definedVariables.add(variable);
			}
			List<LocalVariableInstructionObject> variableInstructions = statement.getLocalVariableInstructions();
			for(LocalVariableInstructionObject variableInstruction : variableInstructions) {
				VariableDeclaration variableDeclaration = null;
				for(VariableDeclaration declaration : variableDeclarationsInMethod) {
					if(declaration.resolveBinding().isEqualTo(variableInstruction.getSimpleName().resolveBinding())) {
						variableDeclaration = declaration;
						break;
					}
				}
				if(variableDeclaration != null) {
					Variable variable = new Variable(variableDeclaration);
					List<Assignment> assignments = statement.getLocalVariableAssignments(variableInstruction);
					List<PostfixExpression> postfixExpressions = statement.getLocalVariablePostfixAssignments(variableInstruction);
					List<PrefixExpression> prefixExpressions = statement.getLocalVariablePrefixAssignments(variableInstruction);
					if(!assignments.isEmpty()) {
						definedVariables.add(variable);
						for(Assignment assignment : assignments) {
							Assignment.Operator operator = assignment.getOperator();
							if(!operator.equals(Assignment.Operator.ASSIGN))
								usedVariables.add(variable);
						}
					}
					else if(!postfixExpressions.isEmpty()) {
						definedVariables.add(variable);
						usedVariables.add(variable);
					}
					else if(!prefixExpressions.isEmpty()) {
						definedVariables.add(variable);
						usedVariables.add(variable);
					}
					else {
						SimpleName variableInstructionName = variableInstruction.getSimpleName();
						if(variableInstructionName.getParent() instanceof MethodInvocation) {
							MethodInvocation methodInvocation = (MethodInvocation)variableInstructionName.getParent();
							if(methodInvocation.getExpression() != null && methodInvocation.getExpression().equals(variableInstructionName)) {
								List<MethodInvocationObject> methodInvocations = statement.getMethodInvocations();
								MethodInvocationObject methodInvocationObject = null;
								for(MethodInvocationObject mio : methodInvocations) {
									if(mio.getMethodInvocation().equals(methodInvocation)) {
										methodInvocationObject = mio;
										break;
									}
								}
								SystemObject systemObject = ASTReader.getSystemObject();
								ClassObject classObject = systemObject.getClassObject(methodInvocationObject.getOriginClassName());
								if(classObject != null) {
									MethodObject methodObject = classObject.getMethod(methodInvocationObject);
									if(methodObject != null) {
										processInternalMethodInvocation(classObject, methodObject, methodInvocation, variableDeclaration,
												new LinkedHashSet<MethodInvocation>());
										List<Expression> arguments = methodInvocation.arguments();
										int argumentPosition = 0;
										for(Expression argument : arguments) {
											if(argument instanceof SimpleName) {
												SimpleName argumentName = (SimpleName)argument;
												VariableDeclaration argumentDeclaration = null;
												for(VariableDeclaration variableDeclaration2 : variableDeclarationsInMethod) {
													if(variableDeclaration2.resolveBinding().isEqualTo(argumentName.resolveBinding())) {
														argumentDeclaration = variableDeclaration2;
														break;
													}
												}
												if(argumentDeclaration != null) {
													ParameterObject parameter = methodObject.getParameter(argumentPosition);
													VariableDeclaration parameterDeclaration = parameter.getSingleVariableDeclaration();
													ClassObject classObject2 = systemObject.getClassObject(parameter.getType().getClassType());
													if(classObject2 != null) {
														processArgumentsOfInternalMethodInvocation(classObject2, methodObject, methodInvocation,
																argumentDeclaration, parameterDeclaration, new LinkedHashSet<MethodInvocation>());
													}
												}
											}
											argumentPosition++;
										}
									}
								}
								else {
									processExternalMethodInvocation(methodInvocation, variableDeclaration);
								}
							}
						}
						usedVariables.add(variable);
					}
				}
			}
			List<FieldInstructionObject> fieldInstructions = statement.getFieldInstructions();
			for(FieldInstructionObject fieldInstruction : fieldInstructions) {
				SystemObject systemObject = ASTReader.getSystemObject();
				ClassObject classObject = systemObject.getClassObject(fieldInstruction.getOwnerClass());
				if(classObject != null) {
					VariableDeclaration fieldDeclaration = null;
					ListIterator<FieldObject> fieldIterator = classObject.getFieldIterator();
					while(fieldIterator.hasNext()) {
						FieldObject fieldObject = fieldIterator.next();
						VariableDeclarationFragment fragment = fieldObject.getVariableDeclarationFragment();
						if(fragment.resolveBinding().isEqualTo(fieldInstruction.getSimpleName().resolveBinding())) {
							fieldDeclaration = fragment;
							break;
						}
					}
					if(fieldDeclaration != null) {
						SimpleName fieldInstructionName = fieldInstruction.getSimpleName();
						VariableDeclaration variableDeclaration = processDirectFieldModification(fieldInstructionName, variableDeclarationsInMethod);
						Variable field = null;
						if(variableDeclaration != null)
							field = new Variable(variableDeclaration, fieldDeclaration);
						else
							field = new Variable(fieldDeclaration);
						List<Assignment> fieldAssignments = statement.getFieldAssignments(fieldInstruction);
						List<PostfixExpression> fieldPostfixAssignments = statement.getFieldPostfixAssignments(fieldInstruction);
						List<PrefixExpression> fieldPrefixAssignments = statement.getFieldPrefixAssignments(fieldInstruction);
						if(!fieldAssignments.isEmpty()) {
							definedVariables.add(field);
							for(Assignment assignment : fieldAssignments) {
								Assignment.Operator operator = assignment.getOperator();
								if(!operator.equals(Assignment.Operator.ASSIGN))
									usedVariables.add(field);
							}
							if(variableDeclaration != null) {
								Variable variable = new Variable(variableDeclaration);
								definedVariables.add(variable);
							}
						}
						else if(!fieldPostfixAssignments.isEmpty()) {
							definedVariables.add(field);
							usedVariables.add(field);
							if(variableDeclaration != null) {
								Variable variable = new Variable(variableDeclaration);
								definedVariables.add(variable);
							}
						}
						else if(!fieldPrefixAssignments.isEmpty()) {
							definedVariables.add(field);
							usedVariables.add(field);
							if(variableDeclaration != null) {
								Variable variable = new Variable(variableDeclaration);
								definedVariables.add(variable);
							}
						}
						else {
							MethodInvocation methodInvocation = null;
							if(fieldInstructionName.getParent() instanceof FieldAccess) {
								FieldAccess fieldAccess = (FieldAccess)fieldInstructionName.getParent();
								if(fieldAccess.getParent() instanceof MethodInvocation) {
									MethodInvocation invocation = (MethodInvocation)fieldAccess.getParent();
									if(fieldAccess.getExpression() instanceof ThisExpression && fieldAccess.getName().equals(fieldInstructionName))
										methodInvocation = invocation;
								}
							}
							else if(fieldInstructionName.getParent() instanceof MethodInvocation) {
								MethodInvocation invocation = (MethodInvocation)fieldInstructionName.getParent();
								if(invocation.getExpression() != null && invocation.getExpression().equals(fieldInstructionName))
									methodInvocation = invocation;
							}
							if(methodInvocation != null) {
								List<MethodInvocationObject> methodInvocations = statement.getMethodInvocations();
								MethodInvocationObject methodInvocationObject = null;
								for(MethodInvocationObject mio : methodInvocations) {
									if(mio.getMethodInvocation().equals(methodInvocation)) {
										methodInvocationObject = mio;
										break;
									}
								}
								ClassObject classObject2 = systemObject.getClassObject(methodInvocationObject.getOriginClassName());
								if(classObject2 != null) {
									MethodObject methodObject = classObject2.getMethod(methodInvocationObject);
									if(methodObject != null) {
										processInternalMethodInvocation(classObject2, methodObject, methodInvocation, fieldDeclaration,
												new LinkedHashSet<MethodInvocation>());
										List<Expression> arguments = methodInvocation.arguments();
										int argumentPosition = 0;
										for(Expression argument : arguments) {
											if(argument instanceof SimpleName) {
												SimpleName argumentName = (SimpleName)argument;
												VariableDeclaration argumentDeclaration = null;
												for(VariableDeclaration variableDeclaration2 : variableDeclarationsInMethod) {
													if(variableDeclaration2.resolveBinding().isEqualTo(argumentName.resolveBinding())) {
														argumentDeclaration = variableDeclaration2;
														break;
													}
												}
												if(argumentDeclaration != null) {
													ParameterObject parameter = methodObject.getParameter(argumentPosition);
													VariableDeclaration parameterDeclaration = parameter.getSingleVariableDeclaration();
													ClassObject classObject3 = systemObject.getClassObject(parameter.getType().getClassType());
													if(classObject3 != null) {
														processArgumentsOfInternalMethodInvocation(classObject3, methodObject, methodInvocation,
																argumentDeclaration, parameterDeclaration, new LinkedHashSet<MethodInvocation>());
													}
												}
											}
											argumentPosition++;
										}
									}
								}
								else {
									processExternalMethodInvocation(methodInvocation, fieldDeclaration);
								}
							}
							usedVariables.add(field);
						}
					}
				}
			}
			List<MethodInvocationObject> methodInvocations = statement.getMethodInvocations();
			for(MethodInvocationObject methodInvocationObject : methodInvocations) {
				MethodInvocation methodInvocation = methodInvocationObject.getMethodInvocation();
				if(methodInvocation.getExpression() == null || methodInvocation.getExpression() instanceof ThisExpression) {
					SystemObject systemObject = ASTReader.getSystemObject();
					ClassObject classObject = systemObject.getClassObject(methodInvocationObject.getOriginClassName());
					if(classObject != null) {
						MethodObject methodObject = classObject.getMethod(methodInvocationObject);
						if(methodObject != null) {
							processInternalMethodInvocation(classObject, methodObject, methodInvocation, null,
									new LinkedHashSet<MethodInvocation>());
							List<Expression> arguments = methodInvocation.arguments();
							int argumentPosition = 0;
							for(Expression argument : arguments) {
								if(argument instanceof SimpleName) {
									SimpleName argumentName = (SimpleName)argument;
									VariableDeclaration argumentDeclaration = null;
									for(VariableDeclaration variableDeclaration2 : variableDeclarationsInMethod) {
										if(variableDeclaration2.resolveBinding().isEqualTo(argumentName.resolveBinding())) {
											argumentDeclaration = variableDeclaration2;
											break;
										}
									}
									if(argumentDeclaration != null) {
										ParameterObject parameter = methodObject.getParameter(argumentPosition);
										VariableDeclaration parameterDeclaration = parameter.getSingleVariableDeclaration();
										ClassObject classObject2 = systemObject.getClassObject(parameter.getType().getClassType());
										if(classObject2 != null) {
											processArgumentsOfInternalMethodInvocation(classObject2, methodObject, methodInvocation,
													argumentDeclaration, parameterDeclaration, new LinkedHashSet<MethodInvocation>());
										}
									}
								}
								argumentPosition++;
							}
						}
					}
				}
			}
		}
	}
}
