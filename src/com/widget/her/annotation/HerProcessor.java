package com.widget.her.annotation;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;

@SupportedAnnotationTypes(value= {"*"})
@SupportedSourceVersion(SourceVersion.RELEASE_6)
public class HerProcessor extends AbstractProcessor {
	private Filer filer;
	private Messager messager;
	
	
    @Override
    public synchronized void init(ProcessingEnvironment env) {
        super.init(env);
        
        filer = env.getFiler();
        messager = env.getMessager();
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> supportTypes = new LinkedHashSet<String>();
        supportTypes.add(GenerateClassSetter.class.getCanonicalName());
        supportTypes.add(FieldToSet.class.getCanonicalName());
        supportTypes.add(GenerateRealmTableHelper.class.getCanonicalName());

        return supportTypes;
    }
    
    class FieldSet {
        int clzId;
        String clzName;
        ArrayList<FieldSetEntry> fieldNames;
        public FieldSet(int clzId, String clzName) {
            this.clzId = clzId;
            this.clzName = clzName;
            fieldNames = new ArrayList<>();
        }
        
        public void addNewTargetField(Element fromField, String targetField, boolean errorCheckStringNull) {
            fieldNames.add(new FieldSetEntry(fromField, targetField, errorCheckStringNull));
        }
    }
    
    class FieldSetEntry {
        Element fromElement;
        String targetField;
        boolean errorCheckStringNull;
                                     
        public FieldSetEntry(Element fromElement, String targetField,
                boolean errorCheckStringNull) {
            this.fromElement = fromElement;
            this.targetField = targetField;
            this.errorCheckStringNull = errorCheckStringNull;
        }

        public Element getFromElement() {
            return fromElement;
        }

        public String getTargetField() {
            return targetField;
        }

        public boolean isErrorCheckStringNull() {
            return errorCheckStringNull;
        }
    }

    public static boolean isStringElement (Element element) {
        return (element.asType().getKind() == TypeKind.DECLARED && 
         ((DeclaredType)element.asType()).asElement().getSimpleName().contentEquals("String"));
    }
    
    public static boolean isNestClassElement(Element element) {
        return (element.getKind() == ElementKind.CLASS && 
                ((TypeElement) element).getNestingKind().isNested());
    }
    
    public static String getElementPackage(Element element) {
        if (isNestClassElement(element)) {
            return getElementPackage(((TypeElement)element).getEnclosingElement());
        }
        return ((PackageElement) element.getEnclosingElement()).getQualifiedName().toString();
    }
    
    // 1. generate at same package under annotattion class exist package
    // 2. add slash char for inner class
    // 3. recursion the set method

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {

        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateClassSetter.class)) {
            messager.printMessage(Kind.WARNING, "Find You! DestSetter", element);

            try {
                GenerateClassSetter setter = element.getAnnotation(GenerateClassSetter.class);
                
                StringBuilder sb = new StringBuilder();
                String packageLoc = getElementPackage(element);
                String fromClass = ((TypeElement)element).getQualifiedName().toString();
                TargetClassDefine[] targetClzs =  setter.targetType();
                HashMap<Integer, FieldSet> bundle = new HashMap<>();
                for (int i = 0; i < targetClzs.length; i++) {
                    bundle.put(Integer.valueOf(targetClzs[i].id()), new FieldSet(targetClzs[i].id(), targetClzs[i].name()));
                }
                for (Element encloseElement : element.getEnclosedElements()) {
                        if (encloseElement.getAnnotation(FieldToSet.class) != null) {
                            FieldToSet fields = encloseElement.getAnnotation(FieldToSet.class);
                            boolean errorCheckStringNull = fields.errorWhenStringNull();
                            for (int i = 0; i < fields.targetFields().length; i++) {
                                TargetFieldDefine targetFieldDefine =  fields.targetFields()[i];
                                FieldSet target = bundle.get(Integer.valueOf((targetFieldDefine.classId())));
                                if (target != null) {
                                    target.addNewTargetField(encloseElement, targetFieldDefine.fieldname(), errorCheckStringNull);
                                }
                            }
                        }
                }
                
                String generateClass = fromClass.substring(packageLoc.length() + 1) // for last '.', which will cause IlleageNameEXception
                        .replaceAll("\\.", "_") + "Setter";
                sb.append("package " + packageLoc + ";\n\n");
                sb.append("public class " + generateClass + " extends " + fromClass + " {\n");
                for (FieldSet fieldSet: bundle.values()) {
                    
                    sb.append("public static void set("+ fromClass +" fromObject, " + fieldSet.clzName  + " targetObject) { \n");
                    for (int i = 0; i < fieldSet.fieldNames.size(); i++) {
                        boolean isStringSet = isStringElement(fieldSet.fieldNames.get(i).getFromElement());
                        String formFieldName = fieldSet.fieldNames.get(i).getFromElement().getSimpleName().toString();
                        String targetFieldName = fieldSet.fieldNames.get(i).getTargetField();
                        String setMethod = "";
                        setMethod = "set" + targetFieldName.substring(0, 1).toUpperCase() + targetFieldName.substring(1);
                        if (isStringSet) { // check null first
                            if (fieldSet.fieldNames.get(i).isErrorCheckStringNull()) { // throw Exception if String null
                                sb.append("if (fromObject." + formFieldName + " == null) throw new RuntimeException(\"" 
                                        + fromClass+"." + formFieldName + " is NULL\"); \n");
                            }
                            sb.append("if (fromObject." + formFieldName + " != null)    ");
                        }
                        sb.append("targetObject." + setMethod + "(fromObject." + formFieldName + ");\n");
                    }
                    sb.append("}\n" );
                }
                sb.append("}\n");

                JavaFileObject jsource = filer.createSourceFile(packageLoc  + "." + generateClass, element);
                jsource.openWriter().append(sb.toString()).close();
                 
            } catch (Exception e) {
                e.printStackTrace();
                messager.printMessage(Kind.ERROR, "cannot generate for class: " + element.getSimpleName(), element);
            }

        }
        
        StringBuilder sb = new StringBuilder();
        int size = roundEnv.getElementsAnnotatedWith(GenerateRealmTableHelper.class).size();
        if (size == 0) return true;
        
        sb.append("package com.widget.her;\n\n");
        sb.append("import io.realm.internal.ColumnType;\n");
        sb.append("import io.realm.internal.ImplicitTransaction;\n");
        sb.append("import io.realm.internal.LinkView;\n");
        sb.append("import io.realm.internal.Table;\n");
        sb.append("import io.realm.internal.TableOrView;\n\n");
        sb.append("public class HerRealmTableHelper {\n");
        
        sb.append("public static void clearTable(Table table) {\n");
        sb.append("    table.clear();\n");
        sb.append("    long size = table.getColumnCount();\n");
        sb.append("    for (long i = 0; i < size; i++) {\n");
        sb.append("        if (table.hasSearchIndex(i)) {\n");
        sb.append("            table.removeSearchIndex(i);\n");
        sb.append("        }\n");
        sb.append("    }\n");
        sb.append("    for (long i = 0; i < size; i++) {\n");
        sb.append("    table.removeColumn(0);\n");
        sb.append("    }\n");
        sb.append("}\n\n");
       
        for (Element element : roundEnv.getElementsAnnotatedWith(GenerateRealmTableHelper.class)) {
            sb.append("    public static void initTable_" + element.getSimpleName() + "(ImplicitTransaction transaction) {\n");
            sb.append("    Table table = transaction.getTable(\"class_" + element.getSimpleName() +  "\");\n");
            sb.append("    clearTable(table);\n\n");
            List<? extends Element> enclosedElements = element.getEnclosedElements();
            String primaryKey = null;
            for (Element e: enclosedElements) {
//                if (e instanceof ExecutableElement) continue;

                System.out.println("-" + e.getSimpleName());
                if (!(e instanceof VariableElement)) continue;
                
                VariableElement var = (VariableElement) e;
                if (var.getConstantValue() != null) continue; // skip final

                if (var.getSimpleName().toString().contains("relation1")) {
                    System.out.println("find userCache");
                }
                TypeKind kind = var.asType().getKind();
                System.out.println(var.getSimpleName());
                if (e.getAnnotation(PrimaryKeyMark.class) != null) {
                    primaryKey = var.getSimpleName().toString();
                }
                
                if (e.getAnnotation(IgnoreKeyMark.class) != null) { // ignore ignore
                    continue;
                }
                if (kind == TypeKind.BOOLEAN) {
                    sb.append("    table.addColumn(ColumnType.BOOLEAN, \"" + var.getSimpleName().toString() + "\");\n");
                } else if (kind == TypeKind.INT) {
                    sb.append("    table.addColumn(ColumnType.INTEGER, \"" + var.getSimpleName().toString() + "\");\n");
                } else if (kind == TypeKind.FLOAT) {
                    sb.append("    table.addColumn(ColumnType.FLOAT, \"" + var.getSimpleName().toString() + "\");\n");
                } else if (kind == TypeKind.DOUBLE) {
                    sb.append("    table.addColumn(ColumnType.DOUBLE, \"" + var.getSimpleName().toString() + "\");\n");
                } else if (kind == TypeKind.LONG) { 
                    sb.append("    table.addColumn(ColumnType.INTEGER, \"" + var.getSimpleName().toString() + "\");\n");
                } else if (isStringElement(e)) {
                    sb.append("    table.addColumn(ColumnType.STRING, \"" + var.getSimpleName().toString() + "\");\n");
                } else if (kind == TypeKind.DECLARED) {
                    if (((DeclaredType)e.asType()).asElement().getSimpleName().contentEquals("RealmList")) {
                        sb.append("    table.addColumnLink(ColumnType.LINK_LIST, \"" + var.getSimpleName().toString() + "\"");
                        String[] splits = ((DeclaredType)e.asType()).getTypeArguments().get(0).toString().split("\\.");
                        String targetTable = "class_" + splits[splits.length - 1];
                        sb.append(", transaction.getTable(\"" + targetTable+"\"));\n");
                    } else {
                        sb.append("    table.addColumnLink(ColumnType.LINK, \"" + var.getSimpleName().toString() + "\"");
                        String[] splits = ((DeclaredType)e.asType()).toString().split("\\.");
                        String targetTable = "class_" + splits[splits.length - 1];
                        sb.append(", transaction.getTable(\"" + targetTable+"\"));\n");
                    }
                }
            }
            // primary key
            if (primaryKey != null) {
                sb.append("\n");
//                sb.append("    table.setIndex(table.getColumnIndex(\"" + primaryKey + "\"));\n");
                sb.append("    table.addSearchIndex(table.getColumnIndex(\"" + primaryKey + "\"));\n");
                sb.append("    table.setPrimaryKey(\"" + primaryKey + "\");\n");
            } else {
                sb.append("    table.setPrimaryKey(\"\");\n");
            }
            sb.append("    }\n");
        }
        sb.append("}\n");
        
         
        try {
            JavaFileObject jsource = filer.createSourceFile("com.widget.her"  + "." + "HerRealmTableHelper");
            jsource.openWriter().append(sb.toString()).close();
        } catch (IOException e) {
            // ZHUJJ Auto-generated catch block
            e.printStackTrace();
        }

        return true;
    } 

}
