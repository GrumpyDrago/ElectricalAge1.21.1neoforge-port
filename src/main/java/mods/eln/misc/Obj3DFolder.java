package mods.eln.misc;

import java.util.*;

import mods.eln.misc.Obj3D.Obj3DPart;

import java.util.Enumeration;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Utility class used to load all eln models and corresponding obj files.
 */
public class Obj3DFolder {

    private Map<String, Obj3D> nameToObjHash = new HashMap<String, Obj3D>();
    private final Obj3D missingObj = new Obj3D();
    private final Set<String> missingObjNamesWarned = new HashSet<String>();

    /**
     * Load all obj models available in the release mod asset folder.
     */
    public void loadAllElnModels() {
        int modelCount = 0;
        for (String path : ElnAssets.list("model", "obj")) {
            String filename = path.substring("model/".length());
            Utils.println(String.format("Loading model %03d '%s'", ++modelCount, filename));
            loadObj(filename);
        }
    }

    /**
     * Load an obj file of a model.
     *
     * @param modelPath path inside model folder (ex. Vumeter/Vumeter.obj)
     */
    private void loadObj(String modelPath) {
        Obj3D obj = new Obj3D();
        if (obj.loadFile(modelPath)) {
            String tag = modelPath.replace(".obj", "");
            tag = tag.substring(tag.lastIndexOf('/') + 1, tag.length());
            if (nameToObjHash.containsKey(key(tag))) {
                Utils.println("Double load of model " + tag);
            }
            nameToObjHash.put(key(tag), obj);    // name of the file, without extension
            Utils.println(String.format(" - model '%s' loaded", modelPath));
        } else {
            Utils.println(String.format(" - unable to load model '%s'", modelPath));
        }
    }

    private static String key(String name) {
        return name.toLowerCase(Locale.ROOT).replace(' ', '_');
    }

    public Obj3D getObj(String obj3DName) {
        Obj3D obj = nameToObjHash.get(key(obj3DName));
        if (obj == null) {
            if (missingObjNamesWarned.add(obj3DName)) {
                Utils.println(String.format("Missing model '%s', using fallback empty model", obj3DName));
            }
            return missingObj;
        }
        return obj;
    }

    public Obj3DPart getPart(String objName, String partName) {
        Obj3D obj = getObj(objName);
        if (obj == null) return null;
        return obj.getPart(partName);
    }

    public void draw(String objName, String partName) {
        Obj3DPart part = getPart(objName, partName);
        if (part != null) part.draw();
    }

    public Set<String> getObjectList() {
        return nameToObjHash.keySet();
    }
}
