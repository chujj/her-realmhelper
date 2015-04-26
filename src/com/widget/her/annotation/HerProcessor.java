package com.widget.her.annotation;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
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
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
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

        return supportTypes;
    }
    
    class FieldSet {
        int clzId;
        String clzName;
        ArrayList<SimpleEntry<Element, String>> fieldNames;
        public FieldSet(int clzId, String clzName) {
            this.clzId = clzId;
            this.clzName = clzName;
            fieldNames = new ArrayList<>();
        }
        
        public void addNewTargetField(Element fromField, String targetField) {
            fieldNames.add(new SimpleEntry<Element, String>(fromField, targetField));
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
                            for (int i = 0; i < fields.targetFields().length; i++) {
                                TargetFieldDefine targetFieldDefine =  fields.targetFields()[i];
                                FieldSet target = bundle.get(Integer.valueOf((targetFieldDefine.classId())));
                                if (target != null) {
                                    target.addNewTargetField(encloseElement, targetFieldDefine.fieldname());
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
                        boolean isStringSet = isStringElement(fieldSet.fieldNames.get(i).getKey());
                        String formFieldName = fieldSet.fieldNames.get(i).getKey().getSimpleName().toString();
                        String targetFieldName = fieldSet.fieldNames.get(i).getValue();
                        String setMethod = "";
                        setMethod = "set" + targetFieldName.substring(0, 1).toUpperCase() + targetFieldName.substring(1);
                        if (isStringSet) { // check null first
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

        return true;
    } 

}
