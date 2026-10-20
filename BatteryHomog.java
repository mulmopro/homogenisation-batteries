import com.comsol.model.*;
import com.comsol.model.util.*;

/**
 * COMSOL Java API model with external text-file inputs.
 *
 * All input files are resolved against the process working directory (user.dir),
 * which is not necessarily the directory containing this Java/class file.
 * Required files: Constants.txt, ParametersH.txt, Anode.txt, Cathode.txt,
 * Separator.txt, Eq_pos.txt, Eq_neg.txt, and geometry.mphbin.
 * Text files are authoritative: their expressions are not overridden here.
 * In the original archive, some text expressions differ from the Java values;
 * importing those files therefore changes the corresponding model settings.
 *
 * The class builds the model tree only: it does not compute geometries,
 * meshes, studies or results, and it does not save the model.
 */
public class BatteryHomog {

  private static final String MODEL_PATH =
      System.getProperty("user.dir");
  private static final String GEOMETRY_FILE =
      java.nio.file.Paths.get(MODEL_PATH, "geometry.mphbin").toString();

  public static Model createModel() {
    validateInputFiles();
    Model model = ModelUtil.create("Model");
    model.modelPath(MODEL_PATH);

    createCellProblem(model);
    createUpscaledProblem(model);
    createStudies(model);

    return model;
  }

  private static void createCellProblem(Model model) {
    model.component().create("comp1", true);
    model.component("comp1").geom().create("geom1", 3);
    model.component("comp1").geom("geom1").geomRep("cadps");
    model.component("comp1").geom("geom1").designBooleans(false);

    model.component("comp1").mesh().create("mesh1");
    model.component("comp1").mesh("mesh1").contribute("geom/detail", true);

    model.param().loadFile(inputFile("Constants.txt"));

    createCellGeometry(model);
    createCellCouplings(model);
    createClosurePhysics(model);

    model.component("comp1").mesh("mesh1").create("ftet1", "FreeTet");
  }

  private static void createCellGeometry(Model model) {
    model.component("comp1").geom("geom1").create("imp1", "Import");
    model.component("comp1").geom("geom1").feature("imp1")
        .set("filename", GEOMETRY_FILE);
    model.component("comp1").geom("geom1").feature("imp1").importData();

    createBoxSelection(model, "boxsel1", "-X", "xmax", "-l/2");
    createBoxSelection(model, "boxsel2", "+X", "xmin", "l/2");
    createBoxSelection(model, "boxsel3", "-Y", "ymax", "-l/2");
    createBoxSelection(model, "boxsel4", "+Y", "ymin", "l/2");
    createBoxSelection(model, "boxsel5", "-Z", "zmax", "-l/2");
    createBoxSelection(model, "boxsel6", "+Z", "zmin", "l/2");

    createUnionSelection(
        model, "comp1", "geom1", "unisel1", "X", 2,
        new String[] {"boxsel1", "boxsel2"});
    createUnionSelection(
        model, "comp1", "geom1", "unisel2", "Y", 2,
        new String[] {"boxsel3", "boxsel4"});
    createUnionSelection(
        model, "comp1", "geom1", "unisel3", "Z", 2,
        new String[] {"boxsel5", "boxsel6"});

    model.component("comp1").geom("geom1").create("ballsel1", "BallSelection");
    model.component("comp1").geom("geom1").feature("ballsel1").label("Solid");
    model.component("comp1").geom("geom1").feature("ballsel1").set("posx", "Xsolid");
    model.component("comp1").geom("geom1").feature("ballsel1").set("posy", "Ysolid");
    model.component("comp1").geom("geom1").feature("ballsel1").set("posz", "Zsolid");
    model.component("comp1").geom("geom1").feature("ballsel1").set("r", 0);

    model.component("comp1").geom("geom1")
        .create("comsel1", "ComplementSelection");
    model.component("comp1").geom("geom1").feature("comsel1").label("Liquid");
    model.component("comp1").geom("geom1").feature("comsel1")
        .set("input", new String[] {"ballsel1"});
  }

  private static void createCellCouplings(Model model) {
    model.component("comp1").cpl().create("intop1", "Integration");
    model.component("comp1").cpl("intop1").set("axisym", true);
    model.component("comp1").cpl("intop1").selection().named("geom1_ballsel1");

    model.component("comp1").cpl().create("intop2", "Integration");
    model.component("comp1").cpl("intop2").set("axisym", true);
    model.component("comp1").cpl("intop2").selection().named("geom1_comsel1");
  }

  private static void createClosurePhysics(Model model) {
    model.component("comp1").physics().create("g", "GeneralFormPDE", "geom1");
    model.component("comp1").physics("g").prop("EquationForm")
        .set("form", "Automatic");
    model.component("comp1").physics("g").selection().named("geom1_ballsel1");
    model.component("comp1").physics("g").field("dimensionless").field("chis");
    model.component("comp1").physics("g").field("dimensionless")
        .component(new String[] {"chis1", "chis2", "chis3"});

    setClosureFluxes(model, "g", "chis");
    createPeriodicConditions(model, "g");
    createMeanZeroConstraints(model, "g", "intop1", "chis");

    model.component("comp1").physics().copy("g2", "g");
    model.component("comp1").physics("g2").selection().named("geom1_comsel1");
    model.component("comp1").physics("g2").field("dimensionless").field("chil");
    model.component("comp1").physics("g2").field("dimensionless")
        .component(new String[] {"chil1", "chil2", "chil3"});

    setClosureFluxes(model, "g2", "chil");
    setMeanZeroConstraints(model, "g2", "intop2", "chil");
  }

  private static void setClosureFluxes(Model model, String physicsTag, String field) {
    model.component("comp1").physics(physicsTag).feature("gfeq1")
        .setIndex("Ga", new String[] {
            "1+d(" + field + "1,x)",
            "d(" + field + "1,y)",
            "d(" + field + "1,z)"
        }, 0);
    model.component("comp1").physics(physicsTag).feature("gfeq1")
        .setIndex("Ga", new String[] {
            "d(" + field + "2,x)",
            "1+d(" + field + "2,y)",
            "d(" + field + "2,z)"
        }, 1);
    model.component("comp1").physics(physicsTag).feature("gfeq1")
        .setIndex("Ga", new String[] {
            "d(" + field + "3,x)",
            "d(" + field + "3,y)",
            "1+d(" + field + "3,z)"
        }, 2);

    for (int i = 0; i < 3; i++) {
      model.component("comp1").physics(physicsTag).feature("gfeq1")
          .setIndex("f", 0, i);
    }
  }

  private static void createPeriodicConditions(Model model, String physicsTag) {
    String[][] selections = {
        {"pc1", "geom1_unisel1", "geom1_boxsel1"},
        {"pc2", "geom1_unisel2", "geom1_boxsel3"},
        {"pc3", "geom1_unisel3", "geom1_boxsel5"}
    };

    for (String[] selection : selections) {
      String tag = selection[0];
      model.component("comp1").physics(physicsTag)
          .create(tag, "PeriodicCondition", 2);
      model.component("comp1").physics(physicsTag).feature(tag)
          .set("manualDestinationSelection", true);
      model.component("comp1").physics(physicsTag).feature(tag)
          .selection().named(selection[1]);
      model.component("comp1").physics(physicsTag).feature(tag)
          .selection("destinationDomains").named(selection[2]);
    }
  }

  private static void createMeanZeroConstraints(
      Model model, String physicsTag, String operator, String field) {
    for (int i = 1; i <= 3; i++) {
      String tag = "gconstr" + i;
      model.component("comp1").physics(physicsTag)
          .create(tag, "GlobalConstraint", -1);
      model.component("comp1").physics(physicsTag).feature(tag)
          .set("constraintExpression", operator + "(" + field + i + ")");
    }
  }

  private static void setMeanZeroConstraints(
      Model model, String physicsTag, String operator, String field) {
    for (int i = 1; i <= 3; i++) {
      model.component("comp1").physics(physicsTag).feature("gconstr" + i)
          .set("constraintExpression", operator + "(" + field + i + ")");
    }
  }

  private static void createUpscaledProblem(Model model) {
    model.component().create("comp2", true);
    model.component("comp2").geom().create("geom2", 1);
    model.component("comp2").mesh().create("mesh2");

    createUpscaledParameters(model);
    createEquilibriumFunctions(model);
    createUpscaledGeometry(model);
    createUpscaledVariables(model);
    createUpscaledPhysics(model);
    createUpscaledMesh(model);
  }

  private static void createUpscaledParameters(Model model) {
    model.param().create("par2");
    model.param("par2").loadFile(inputFile("ParametersH.txt"));
  }

  private static void createEquilibriumFunctions(Model model) {
    createEquilibriumFunction(model, "int1", "Eq_pos", "Eq_pos.txt");
    createEquilibriumFunction(model, "int2", "Eq_neg", "Eq_neg.txt");
  }

  private static void createEquilibriumFunction(
      Model model, String tag, String functionName, String filename) {
    model.func().create(tag, "Interpolation");
    model.func(tag).set("source", "file");
    model.func(tag).set("filename", inputFile(filename));
    model.func(tag).set("struct", "spreadsheet");
    model.func(tag).set("nargs", 1);
    model.func(tag).set("funcs", new String[][] {{functionName, "1"}});
    model.func(tag).set("argunit", "1");
    model.func(tag).set("fununit", "V");
    model.func(tag).importData();
  }

  private static void createUpscaledGeometry(Model model) {
    model.component("comp2").geom("geom2").create("i1", "Interval");
    model.component("comp2").geom("geom2").feature("i1").set("specify", "len");
    model.component("comp2").geom("geom2").feature("i1")
        .setIndex("len", "L_neg", 0);
    model.component("comp2").geom("geom2").feature("i1")
        .setIndex("len", "L_sep", 1);
    model.component("comp2").geom("geom2").feature("i1")
        .setIndex("len", "L_pos", 2);

    createBallSelection1D(model, "ballsel1", "Anode-CC", "0", 0);
    createBallSelection1D(model, "ballsel2", "Anode", "L_neg/2", 1);
    createBallSelection1D(model, "ballsel3", "Anode-Sep", "L_neg", 0);
    createBallSelection1D(model, "ballsel4", "Separator", "L_neg+L_sep/2", 1);
    createBallSelection1D(model, "ballsel5", "Cathode-Sep", "L_neg+L_sep", 0);
    createBallSelection1D(
        model, "ballsel6", "Cathode", "L_neg+L_sep+L_pos/2", 1);
    createBallSelection1D(
        model, "ballsel7", "Cathode-CC", "L_neg+L_sep+L_pos", 0);

    createUnionSelection(
        model, "comp2", "geom2", "unisel1", "Liquid", 1,
        new String[] {"ballsel2", "ballsel4", "ballsel6"});
    createUnionSelection(
        model, "comp2", "geom2", "unisel2", "Solid", 1,
        new String[] {"ballsel2", "ballsel6"});
    createUnionSelection(
        model, "comp2", "geom2", "unisel3", "Solid-Sep", 0,
        new String[] {"ballsel3", "ballsel5"});
  }

  private static void createUpscaledVariables(Model model) {
    model.component("comp2").variable().create("var1");
    model.component("comp2").variable("var1").loadFile(inputFile("Anode.txt"));
    model.component("comp2").variable("var1").selection().geom("geom2", 1);
    model.component("comp2").variable("var1").selection().named("geom2_ballsel2");

    model.component("comp2").variable().create("var2");
    model.component("comp2").variable("var2").loadFile(inputFile("Cathode.txt"));
    model.component("comp2").variable("var2").selection().geom("geom2", 1);
    model.component("comp2").variable("var2").selection().named("geom2_ballsel6");

    model.component("comp2").variable().create("var3");
    model.component("comp2").variable("var3").loadFile(inputFile("Separator.txt"));
    model.component("comp2").variable("var3").selection().geom("geom2", 1);
    model.component("comp2").variable("var3").selection().named("geom2_ballsel4");
  }

  private static void createUpscaledPhysics(Model model) {
    createGeneralFormPde(model, "g3", "Liquid - Mass", "cl");
    createGeneralFormPde(model, "g4", "Liquid - Charge", "pl");
    createGeneralFormPde(model, "g5", "Solid - Mass", "cs");
    createGeneralFormPde(model, "g6", "Solid - Charge", "ps");

    configureLiquidMass(model);
    configureLiquidCharge(model);
    configureSolidMass(model);
    configureSolidCharge(model);
  }

  private static void createGeneralFormPde(
      Model model, String tag, String label, String field) {
    model.component("comp2").physics().create(tag, "GeneralFormPDE", "geom2");
    model.component("comp2").physics(tag).label(label);
    model.component("comp2").physics(tag).prop("EquationForm")
        .set("form", "Automatic");
    model.component("comp2").physics(tag).field("dimensionless").field(field);
    model.component("comp2").physics(tag).field("dimensionless")
        .component(1, field);
    model.component("comp2").physics(tag).prop("Units")
        .set("CustomDependentVariableUnit", "1");
    model.component("comp2").physics(tag).prop("Units")
        .set("DependentVariableQuantity", "none");
  }

  private static void configureLiquidMass(Model model) {
    String tag = "g3";
    setPdeUnits(model, tag, "mol/m^3", "mol/s/m^3");
    model.component("comp2").physics(tag).selection().named("geom2_unisel1");
    model.component("comp2").physics(tag).feature("gfeq1").setIndex(
        "Ga",
        "(-Dl_eff + 2*R*T*t_plus*(1-t_plus)/F^2*Kl_eff/cl)*d(cl,x)"
            + " - t_plus/F*Kl_eff*d(pl,x)",
        0);
    model.component("comp2").physics(tag).feature("gfeq1")
        .setIndex("f", "2*eta*K_star/F*i_BV", 0);
    model.component("comp2").physics(tag).feature("gfeq1")
        .setIndex("da", "eta", 0);
    model.component("comp2").physics(tag).feature("init1")
        .set("cl", "cl_init");
  }

  private static void configureLiquidCharge(Model model) {
    String tag = "g4";
    setPdeUnits(model, tag, "V", "A/m^3");
    model.component("comp2").physics(tag).selection().named("geom2_unisel1");
    model.component("comp2").physics(tag).feature("gfeq1").setIndex(
        "Ga",
        "-2*R*T*(1-t_plus)/F*Kl_eff/cl*d(cl,x) + Kl_eff*d(pl,x)",
        0);
    model.component("comp2").physics(tag).feature("gfeq1")
        .setIndex("f", "-2*eta*K_star*i_BV", 0);
    model.component("comp2").physics(tag).feature("gfeq1")
        .setIndex("da", 0, 0);
    model.component("comp2").physics(tag).feature("init1")
        .set("pl", "pl_init");
  }

  private static void configureSolidMass(Model model) {
    String tag = "g5";
    setPdeUnits(model, tag, "mol/m^3", "mol/s/m^3");
    model.component("comp2").physics(tag).selection().named("geom2_unisel2");
    model.component("comp2").physics(tag).feature("gfeq1")
        .setIndex("Ga", "-Ds_eff*d(cs,x)", 0);
    model.component("comp2").physics(tag).feature("gfeq1")
        .setIndex("f", "-2*eta*K_star/F*i_BV", 0);
    model.component("comp2").physics(tag).feature("init1")
        .set("cs", "cs_init");

    model.component("comp2").physics(tag).create("flux1", "FluxBoundary", 0);
    model.component("comp2").physics(tag).feature("flux1")
        .selection().named("geom2_unisel3");
    model.component("comp2").physics(tag).feature("flux1")
        .setIndex("g", "-i_BV/F*A_cell_neg/(l*l)", 0);
  }

  private static void configureSolidCharge(Model model) {
    String tag = "g6";
    setPdeUnits(model, tag, "V", "A/m^3");
    model.component("comp2").physics(tag).selection().named("geom2_unisel2");
    model.component("comp2").physics(tag).feature("gfeq1")
        .setIndex("Ga", "Ks_eff*d(ps,x)", 0);
    model.component("comp2").physics(tag).feature("gfeq1")
        .setIndex("f", "2*eta*K_star*i_BV", 0);
    model.component("comp2").physics(tag).feature("init1")
        .set("ps", "ps_init");

    model.component("comp2").physics(tag)
        .create("dir1", "DirichletBoundary", 0);
    model.component("comp2").physics(tag).feature("dir1")
        .selection().named("geom2_ballsel1");

    model.component("comp2").physics(tag)
        .create("flux1", "FluxBoundary", 0);
    model.component("comp2").physics(tag).feature("flux1")
        .selection().named("geom2_ballsel7");
    model.component("comp2").physics(tag).feature("flux1")
        .setIndex("g", "-J", 0);
  }

  private static void setPdeUnits(
      Model model, String physicsTag, String dependentUnit, String sourceUnit) {
    model.component("comp2").physics(physicsTag).prop("Units")
        .setIndex("CustomDependentVariableUnit", dependentUnit, 0, 0);
    model.component("comp2").physics(physicsTag).prop("Units")
        .setIndex("CustomSourceTermUnit", sourceUnit, 0, 0);
  }

  private static void createUpscaledMesh(Model model) {
    model.component("comp2").mesh("mesh2").create("edg1", "Edge");
    model.component("comp2").mesh("mesh2").feature("size").set("hauto", 4);
  }

  private static void createStudies(Model model) {
    model.study().create("std1");

    model.study("std1").create("stat", "Stationary");
    model.study("std1").feature("stat")
        .setSolveFor("/modelNode/comp2", false);

    model.study("std1").create("time", "Transient");
    model.study("std1").feature("time")
        .setSolveFor("/modelNode/comp1", false);
    model.study("std1").feature("time")
        .set("tlist", "range(t_init,t_sample,t_final)");
    model.study("std1").feature("time").set("usertol", true);
    model.study("std1").feature("time").set("rtol", "0.0001");
  }

  private static void createBoxSelection(
      Model model, String tag, String label, String limit, String value) {
    model.component("comp1").geom("geom1").create(tag, "BoxSelection");
    model.component("comp1").geom("geom1").feature(tag).label(label);
    model.component("comp1").geom("geom1").feature(tag).set("entitydim", 2);
    model.component("comp1").geom("geom1").feature(tag).set(limit, value);
    model.component("comp1").geom("geom1").feature(tag)
        .set("condition", "allvertices");
  }

  private static void createBallSelection1D(
      Model model, String tag, String label, String position, int entityDimension) {
    model.component("comp2").geom("geom2").create(tag, "BallSelection");
    model.component("comp2").geom("geom2").feature(tag).label(label);
    model.component("comp2").geom("geom2").feature(tag).set("posx", position);
    model.component("comp2").geom("geom2").feature(tag)
        .set("entitydim", entityDimension);
  }

  private static void createUnionSelection(
      Model model,
      String component,
      String geometry,
      String tag,
      String label,
      int entityDimension,
      String[] input) {
    model.component(component).geom(geometry).create(tag, "UnionSelection");
    model.component(component).geom(geometry).feature(tag).label(label);
    model.component(component).geom(geometry).feature(tag)
        .set("entitydim", entityDimension);
    model.component(component).geom(geometry).feature(tag).set("input", input);
  }

  private static String inputFile(String filename) {
    return java.nio.file.Paths.get(MODEL_PATH, filename).toString();
  }

  private static void validateInputFiles() {
    String[] filenames = {
        "Constants.txt", "ParametersH.txt", "Anode.txt", "Cathode.txt",
        "Separator.txt", "Eq_pos.txt", "Eq_neg.txt", "geometry.mphbin"
    };
    for (String filename : filenames) {
      java.nio.file.Path path = java.nio.file.Paths.get(MODEL_PATH, filename);
      if (!java.nio.file.Files.isRegularFile(path)
          || !java.nio.file.Files.isReadable(path)) {
        throw new IllegalArgumentException("Missing or unreadable input file: "
            + path.toAbsolutePath()
            + ". Start COMSOL from the project directory or set MODEL_PATH.");
      }
    }
  }

  public static Model run() {
    return createModel();
  }

  public static void main(String[] args) {
    run();
  }
}
