package se.linuxgods.kreiger;

import com.intellij.codeInspection.InspectionToolProvider;
import com.intellij.openapi.components.ApplicationComponent;
import org.jetbrains.annotations.NotNull;

public class UselessJavadocApplicationComponent implements ApplicationComponent, InspectionToolProvider {

    public static final Class[] INSPECTION_CLASSES = { UselessJavadocInspection.class };

    public UselessJavadocApplicationComponent() {
    }

    public void initComponent() {
        // TODO: insert component initialization logic here
    }

    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @NotNull
    public String getComponentName() {
        return "se.linuxgods.kreiger.UselessJavadocApplicationComponent";
    }


    @Override
    public Class[] getInspectionClasses() {
        return INSPECTION_CLASSES;
    }
}
