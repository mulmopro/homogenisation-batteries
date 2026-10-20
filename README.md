# Homogenized Lithium-Ion Battery Model — COMSOL Java API

COMSOL Java API implementation of a multiscale battery model combining periodic, three-dimensional cell problems with a one-dimensional homogenized electrochemical model.

> **Status:** research model under review. The supplied Java source constructs a model tree but does not explicitly build the geometries or meshes, solve the study, export results, or save an MPH file. The current snapshot has known implementation and consistency issues listed below. A Windows / COMSOL 6.4 test reached Java-class execution but stopped because Design Module Boolean operations were enabled while the module was unavailable. Successful model construction and numerical validation have not yet been confirmed.

## Model overview

The model contains two components:

| Component | Purpose | Dependent variables |
| --- | --- | --- |
| `comp1` — 3D | Periodic closure problems in the solid and liquid phases of an imported cell geometry | `chis1`–`chis3`, `chil1`–`chil3` |
| `comp2` — 1D | Mass and charge transport across negative electrode, separator, and positive electrode | `cs`, `cl`, `ps`, `pl` |

The closure problems impose periodic conditions on opposite cell faces and integral zero-mean constraints on each corrector. Their fluxes have the form `e_j + grad(chi_j)`. Only the longitudinal `zz` closure integrals are used in the 1D transport coefficients; the microscopic `z` direction is mapped to the macroscopic `x` direction.

Both electrodes currently use the same closure solution. Different electrode morphologies require separate closure calculations or independently supplied effective coefficients.

The 1D model uses General Form PDE interfaces for liquid mass, liquid charge, solid mass, and solid charge. Electrode reactions use Butler–Volmer-type expressions with tabulated equilibrium potentials. Solid concentration is a macroscopic field defined on the electrode intervals; this implementation does not add a radial particle-diffusion coordinate as in a conventional DFN/P2D model.

## Repository contents

| File | Role |
| --- | --- |
| `BatteryHomog.java` | Model construction, external-file imports, PDEs, mesh definitions, and study settings |
| `geometry.mphbin` | COMSOL binary geometry imported by the 3D component |
| `Constants.txt` | Cell position and length parameters loaded into global parameters |
| `ParametersH.txt` | Macroscopic and material parameters loaded into `par2` |
| `Anode.txt` | Negative-electrode variables loaded into `comp2/var1` |
| `Cathode.txt` | Positive-electrode variables loaded into `comp2/var2` |
| `Separator.txt` | Separator variables loaded into `comp2/var3` |
| `Eq_neg.txt` | Negative-electrode equilibrium-potential table |
| `Eq_pos.txt` | Positive-electrode equilibrium-potential table |

**The text files are the source of parameter values, variable expressions, and equilibrium-potential data.** The Java source loads the first five text files with `loadFile()`. It creates file-based interpolation functions for `Eq_pos.txt` and `Eq_neg.txt` and imports them with `importData()`. The embedded `EQ_POS_TABLE` and `EQ_NEG_TABLE` arrays have been removed.

The equilibrium files contain two numeric columns: dimensionless stoichiometry and potential in volts. Parameter/variable files use COMSOL's name, expression, and optional description format, for example:

```text
L_neg "100 [um]" "Negative electrode thickness"
cs_max_neg "31507 [mol/m^3]" ""
```

Change values in the text files, then recreate the model by opening/running the class again. Editing an external file does not automatically update an already constructed model. Changes to text inputs do not require Java recompilation; changes to Java do.

The original `Final.zip` contained differences between the embedded Java expressions and the text files. The rewritten code reads the text expressions as supplied; it does not silently restore the old Java values. See the migration notes below.

## Input paths

The project directory is obtained from the Java process working directory:

```java
private static final String MODEL_PATH =
    System.getProperty("user.dir");
```

Paths are joined with `java.nio.file.Paths.get(...)`, which supports Windows and Linux separators. All eight input files must be in this directory. An initial check reports any missing or unreadable file before model construction begins.

**The working directory is not necessarily the directory containing the `.java` or `.class` file.** This is especially relevant when COMSOL is launched from a desktop shortcut. Start COMSOL from the project directory and inspect the reported path if an input cannot be found. For a local diagnostic test, an explicit path is also possible:

```java
private static final String MODEL_PATH = "C:/COMSOL_models/Battery";
```

Recompile after changing this Java setting. Neither `System` nor `String` needs an import; the path APIs are fully qualified in the source.

## Requirements

- A licensed COMSOL Multiphysics installation with the Java API and General Form PDE functionality.
- Geometry capabilities compatible with the imported file and the requested `cadps` geometry representation / design Boolean settings. Check the required products against the actual installation.
- COMSOL's Java compilation tools, or a correctly configured COMSOL Java environment. A standard JDK alone is insufficient.

The originating COMSOL version is not recorded in the original archive. The instructions below target Windows PowerShell with COMSOL 6.4; that environment has reached class execution, but a complete run is not yet confirmed. Record the final required products after a successful run. No MATLAB or Python runtime is used by the model itself.

## Compile and test on Windows (PowerShell)

1. Put `BatteryHomog.java`, `geometry.mphbin`, and all seven `.txt` files in one directory, for example `C:\COMSOL_models\Battery`.
2. Check the geometry-module settings described below before compiling. The latest supplied source still enables Design Module Boolean operations; the observed installation requires that setting to be disabled.
3. Open PowerShell and enter the project directory:

   ```powershell
   Set-Location "C:\COMSOL_models\Battery"
   ```

4. Compile with the COMSOL compiler, adjusting the installation path if needed:

   ```powershell
   & "C:\Program Files\COMSOL\COMSOL64\Multiphysics\bin\win64\comsolcompile.exe" BatteryHomog.java
   ```

   A successful compilation produces `BatteryHomog.class`. In PowerShell, the `&` call operator is required before a quoted executable path. Without it, PowerShell can report `UnexpectedToken` for `BatteryHomog.java` before the compiler even starts.

5. Launch a new COMSOL session from the same PowerShell window:

   ```powershell
   & "C:\Program Files\COMSOL\COMSOL64\Multiphysics\bin\win64\comsol.exe"
   ```

6. In COMSOL, select **File > Open**, choose **Model Class File (*.class)** if needed, and open `BatteryHomog.class`.
7. Inspect the imported global parameters, `par2`, all three variable groups, and both interpolation functions.
8. Build both geometries and inspect every domain and boundary selection. Verify that the origin-based solid selection covers the intended solid domains, its complement identifies liquid, and opposite-face periodic selections are valid.
9. Build both meshes. Resolve the physical and dimensional issues below before computing the study.
10. Compute the stationary closure step followed by the time-dependent electrochemical step. Confirm that variables not solved in the transient step use the stationary corrector solution.
11. Save the model explicitly as an `.mph` file and configure plots/exports as required.

The class is already named `BatteryHomog` and includes `run()`, `createModel()`, and `main()`. There is no need to rename it or add another entry point. These methods construct the model tree; they do not explicitly execute geometry, mesh, or study `run()` calls or save a model. Input imports occur during construction.

### Geometry modules: observed error and workaround

The following error was observed when opening the class:

```text
Cannot enable Design Module Boolean operations.
Design Module is not installed
```

In `createCellProblem(...)`, replace:

```java
model.component("comp1").geom("geom1").designBooleans(true);
```

with:

```java
model.component("comp1").geom("geom1").designBooleans(false);
```

Recompile and reopen the class. This disables the optional Design Module Boolean operations; it does not install or grant access to a module.

If a subsequent error concerns the CAD/Parasolid kernel, replace:

```java
model.component("comp1").geom("geom1").geomRep("cadps");
```

with:

```java
model.component("comp1").geom("geom1").geomRep("comsol");
```

This selects the native COMSOL geometry kernel. Rebuild and check the imported geometry and selections: compatibility and geometry equivalence have not been verified for this file. The Design Module change is the immediate fix for the observed error; switching geometry kernels is a separate conditional step.

### Linux / macOS

With COMSOL available on `PATH`, compile from the project directory:

```bash
comsol compile BatteryHomog.java
```

Then open the resulting class in COMSOL Desktop, taking the same working-directory and geometry-module precautions.

## Default configuration from the supplied text files

| Quantity | Value |
| --- | --- |
| Negative / positive electrode thickness | `100 um` each |
| Separator thickness | `25 um` |
| Cell length parameter | `5 um` |
| Temperature | `300 K` |
| Initial electrolyte concentration | `1000 mol/m^3` |
| Maximum negative / positive solid concentration | `31507` / `49000 mol/m^3` |
| Initial negative / positive stoichiometry | `0.05` / `0.99` |
| Electrode liquid fraction from supplied volumes | `0.260736` for both electrodes |
| Separator liquid fraction | `1` |
| Electrolyte diffusivity | `4.0383e-10 m^2/s` |
| Defined negative / positive solid diffusivity | `1.3166e-13` / `1e-13 m^2/s` |
| Electrolyte conductivity | `0.95 S/m` |
| Solid conductivity | `100 S/m` for both electrodes |
| C-rate parameter | `0.33 1/h` |
| Final time | `0.95/C_rate`, approximately `10363.64 s` |
| Requested output spacing | `t_final/1000`, approximately `10.364 s` |
| Relative transient tolerance (set in Java) | `1e-4` |

The time list requests 1001 output times. These are not prescribed internal solver time steps. The applied load is constant; no pulse sequence or current ramp is defined.

The positive solid diffusivity is listed as a *defined* parameter because the current cathode expression incorrectly references `Ds_neg`.

## Known issues in the supplied snapshot

### Migration notes and remaining text-file issues

The filename/class mismatch and hard-coded author path have been addressed. Equilibrium functions now explicitly use dimensionless arguments and voltage outputs. Parameter and variable values are loaded from files rather than duplicated in Java.

| Location | Current behavior / remaining issue | Action |
| --- | --- | --- |
| `Cathode.txt`: `Ds_eff` | Uses `Ds_neg*cf_s_pos`; `Ds_pos` is defined but not used here | Use `Ds_pos*cf_s_pos` if distinct diffusivities are intended. |
| `ParametersH.txt`: `V_tot_neg` | Defined as `V_s_pos+V_l_pos` | Use negative-electrode volumes. Equal current values hide the error. |
| Scale ratio | Global `epsilon_neg/pos` use `L/l`, while local `epsilon` uses `l/L` | Adopt one convention; current values are `20` and `0.05`. These variables are not explicitly used in the shown PDE expressions. |
| `Jps` migration | The text expression omits the `0.95` factor present in the original Java | The text expression now takes effect. Confirm the intended capacity convention. |
| Closure-factor migration | Electrode text files contain only `intop(...)`; the original Java used `intop(...)/(l^3)*1[m]` | The raw integral now takes effect. Resolve normalization and units in the text files. |
| `Anode.txt`: `cs_init` | Third field is `0.95*cs_max_neg`, while the expression is `0.05*cs_max_neg` | Clarify/remove the misleading description field; it is not a second initial value. |
| `f_init`, `f_step`, `f_final` | Loaded from `ParametersH.txt` but not explicitly used in the Java equations/study | Identify as legacy entries or implement their intended use. |

These notes refer to the original supplied text files. If those files are revised, their current expressions govern the model.

### Physical and numerical issues requiring resolution

1. **Corrector units and effective-coefficient normalization.** The closure fields are not explicitly assigned length units, despite terms such as `1+d(chil3,z)`. For a dimensional cell coordinate and dimensional corrector, a consistent full-cell factor is `intop(1+d(chi3,z))/V_cell`, which is dimensionless. Multiplying this by `1[m]` gives it length units. Conversely, a dimensionless corrector differentiated with respect to dimensional `z` makes the sum with `1` inconsistent. Select a fully dimensional or fully nondimensional formulation and implement it consistently. The currently supplied text files use raw integrals without division by cell volume; these are also unsuitable as dimensionless transport factors for a dimensional 3D cell. The extra `1[m]` belonged to the original embedded Java expressions and is no longer inserted by the rewritten code. Resolve the normalization in the electrode text files and confirm that `V_cell=l^3` agrees with the imported geometry.

2. **Equilibrium-potential range.** Units are now explicitly configured as dimensionless arguments and voltage outputs. `Eq_pos` ends at stoichiometry `0.975`, but the positive electrode starts at `0.99`; its initial potential therefore depends on extrapolation. Supply justified data over the operating range or choose a supported initial state.

3. **Initial overpotentials.** With `ps_neg=0`, `pl=0`, and `ps_pos=Eq_pos(0.99)-Eq_neg(0.05)`, both initial overpotentials equal `-Eq_neg(0.05)=-0.313705 V`. This is not an equilibrium initialization. If equilibrium is intended with the negative solid grounded, a consistent electrolyte guess is `pl_init=-Eq_neg(0.05)` throughout the cell. A loaded initial state still requires a consistent algebraic solve.

4. **Explicit time coefficients.** Liquid charge sets `da=0`, but solid charge does not explicitly set `da`; its behavior therefore depends on the interface defaults. For quasistatic solid charge, explicitly set `da=0` and verify `ea=0`. Solid mass also leaves `da` implicit. If `cs` is an intrinsic solid-phase concentration, check whether its accumulation should be weighted by `1-eta` when transport and reaction terms are expressed per total volume. Document the concentration convention before changing this coefficient.

5. **Geometry, volume, and current scaling.** The supplied phase volumes sum to `2.5e-15 m^3`, whereas `A_cell_neg*L_neg=1.5528e-15 m^3`; the ratio is approximately `1.610`. If `A_cell` is the macroscopic cross-section, these values disagree. If it denotes a different area, document it and derive the current conversion. Using the original text-file expressions now gives `Jps≈33.167 A/m^2` and applied `J≈27.866 A/m^2`. The `0.95` factor remains in the duration but is absent from the text-file current expression. These values differ from the previous embedded Java version (`Jps≈31.508 A/m^2`, `J≈26.473 A/m^2`). Document the intended capacity and area convention.

6. **Reaction prefactors and conservation.** Since `eta*K_star=S/V_tot`, the volume source uses `2*S/V_tot*i_BV`. Verify whether `S` is the full reactive interface area or a half-area convention. The factor `2` in the symmetric Butler–Volmer expression has a separate origin and is not automatically erroneous. Check the kinetic prefactors against their source data. Liquid/solid mass volume sources are opposite, and liquid/solid charge volume sources are opposite, which is structurally consistent; it does not by itself prove global conservation.

7. **Solid–separator mass flux.** `g5/flux1` imposes `-i_BV/F*A_cell_neg/(l*l)` at both electrode–separator interfaces, including the positive side. Determine whether a separate boundary reaction is intended in addition to the volumetric reaction. The liquid mass equation has no corresponding explicitly added interface source. Derive the coupled interface balance and check global lithium conservation; use electrode-specific areas if appropriate.

8. **Concentration admissibility.** The negative-electrode reaction uses absolute values and an abrupt cutoff at `cs_max_neg`; these can hide nonphysical negative concentrations and suppress both reaction directions at saturation. The positive expression has unprotected square roots. Monitor `cl>0` and `0<cs/cs_max<1`; use physically justified termination criteria or regularization.

9. **Geometry selections and closure solvability.** The binary geometry has not been inspected in COMSOL. Verify cell dimensions, phase connectivity, selected volumes, periodic matching, and zero-mean constraints. A single mean constraint per corrector may not remove all null modes if a phase has disconnected components. Zero-radius ball selections are supported by the documented selection defaults; their use alone is not evidence of an error.

10. **Study handoff.** The two study steps disable the opposite component, but there is no explicit solver configuration demonstrating that the transient step retains the stationary closure fields. Inspect the generated solver and the values of variables not solved for before accepting the effective coefficients.

## Validation before using results

- Compile and construct the model in the recorded COMSOL version.
- Resolve all unit inconsistencies and verify every geometric selection.
- Compare imported phase volumes and reactive area with the parameter values.
- Check corrector periodicity, zero means, effective coefficients, and mesh convergence.
- Run a zero-current equilibrium test with consistent initial potentials.
- Check global lithium inventory, total current continuity, and the integrated reaction/current balance, including all boundary contributions.
- Check concentration bounds and sensitivity to mesh, solver tolerances, and initialization.
- Compare voltage and concentration histories with a trusted model or dataset using matching geometry, material parameters, and load.

No reference solution, simulation log, experimental dataset, manuscript, or automated regression tests are included. Static consistency review cannot establish agreement with a particular theoretical derivation or publication.

## Results and reproducibility

No plot groups, derived-value evaluations, or result-export nodes are explicitly configured. After validation, useful outputs include terminal voltage, phase concentrations and potentials, closure factors, and conservation residuals.

Record the COMSOL version, required products, parameter set, geometry provenance, mesh statistics, solver settings, and benchmark results alongside published results. Document the chemistry and sources of the equilibrium-potential tables; these are not identified in the supplied files.

## Documentation

- [COMSOL commands on Windows](https://doc.comsol.com/6.4/doc/com.comsol.help.comsol/comsol_ref_running.38.31.html)
- [Parameter and variable file import](https://doc.comsol.com/6.4/doc/com.comsol.help.comsol/comsol_api_general.47.13.html)
- [Geometry kernel and Design Module options](https://doc.comsol.com/6.3/doc/com.comsol.help.comsol/comsol_api_geom.48.025.html)
- [COMSOL API overview](https://www.comsol.com/support/learning-center/article/overview-of-the-comsol-api-107912)
- [General Form PDE](https://doc.comsol.com/6.3/doc/com.comsol.help.comsol/comsol_ref_equationbased.32.008.html)
- [Integration operators](https://doc.comsol.com/6.3/doc/com.comsol.help.comsol/comsol_ref_definitions.21.096.html)
- [Function API and interpolation settings](https://doc.comsol.com/6.3/doc/com.comsol.help.comsol/comsol_api_general.47.34.html)
- [Geometry selections](https://doc.comsol.com/6.4/doc/com.comsol.help.comsol/comsol_api_geom.48.060.html)

Documentation versions above are API references. The observed Windows / COMSOL 6.4 test stopped at the missing Design Module setting; a successful full run is not claimed.

## License and citation

No license or publication citation is supplied in this snapshot. The repository owner should add an appropriate `LICENSE` file and the correct publication reference before release. A COMSOL license is required independently of any license chosen for this source code.
