# Homogenized Lithium-Ion Battery Model — COMSOL Java API

COMSOL Java API implementation of a multiscale battery model combining periodic, three-dimensional cell problems with a one-dimensional homogenized electrochemical model.

> **Status:** research model under review. The supplied Java source constructs a model tree but does not explicitly build the geometries or meshes, solve the study, export results, or save an MPH file. The current snapshot has known implementation and consistency issues listed below. It has not been compiled or numerically validated as part of this review.

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
| `BatteryHomog.java` | Model construction, embedded parameters, equilibrium tables, variables, PDEs, mesh definitions, and study settings |
| `geometry.mphbin` | COMSOL binary geometry imported by the 3D component |
| `Constants.txt` | Reference cell position and length parameters |
| `ParametersH.txt` | Reference macroscopic and material parameters |
| `Anode.txt` | Reference negative-electrode variables |
| `Cathode.txt` | Reference positive-electrode variables |
| `Separator.txt` | Reference separator variables |
| `Eq_neg.txt` | Negative-electrode equilibrium-potential table |
| `Eq_pos.txt` | Positive-electrode equilibrium-potential table |

**The Java source does not read the `.txt` files.** Parameters, variables, and equilibrium tables are embedded in the source. Editing a text file alone therefore does not change the generated model. The only external data file explicitly imported by the Java code is `geometry.mphbin`.

The two equilibrium tables match their Java counterparts numerically: 100 rows for the negative electrode and 16 rows for the positive electrode. Other reference files are not fully synchronized with the source.

## Requirements

- A licensed COMSOL Multiphysics installation with the Java API and General Form PDE functionality.
- Geometry capabilities compatible with the imported file and the requested `cadps` geometry representation / design Boolean settings. Check the required products against the actual installation.
- COMSOL's Java compilation tools, or a correctly configured COMSOL Java environment. A standard JDK alone is insufficient.

The originating COMSOL version and licensed products are not recorded in the supplied archive. Add the tested version, operating system, and required products after a successful run. No MATLAB or Python runtime is used by the model itself.

## Preparing and opening the model

The following steps describe preparation of the current snapshot, not a verified one-command simulation.

1. Place the Java source and `geometry.mphbin` in the same project directory.
2. In `BatteryHomog.java`, change `public class test6_compact` to `public class BatteryHomog`. Java requires the public class name and filename to agree.
3. Change `MODEL_PATH` to the absolute path of that project directory. The supplied value is an author-specific Linux path.
4. For the usual COMSOL model-file entry point, add this method inside the class:

   ```java
   public static Model run() {
     return createModel();
   }
   ```

   The existing `main` can call `run()`. This wrapper constructs the model; it does not solve it.

5. Compile using the tools provided by your installed COMSOL version. Typical commands are:

   ```bash
   # Linux / macOS, with COMSOL on PATH
   comsol compile BatteryHomog.java
   ```

   ```powershell
   # Windows, from a shell with COMSOL's bin directory on PATH
   comsolcompile BatteryHomog.java
   ```

6. Open the resulting model class file in COMSOL Desktop. Resolve any version-specific API or geometry-kernel errors before continuing.
7. Build both geometries and inspect every domain and boundary selection. In particular, verify that the origin-based solid selection identifies all intended solid domains, the liquid complement is correct, and all opposite-face pairs are valid.
8. Build both meshes. Review and resolve the physical and dimensional issues below before computing.
9. Compute the stationary closure step, followed by the time-dependent electrochemical step. Confirm that the latter uses the stationary corrector solution for variables not solved in that step.
10. Save the completed model explicitly from COMSOL Desktop. Add plots and exports as needed.

The Java code calls `importData()` during model construction, so the geometry file must already be accessible even though there is no explicit geometry `run()` call.

## Default configuration in the Java source

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
| Relative transient tolerance | `1e-4` |

The time list requests 1001 output times. These are not prescribed internal solver time steps. The applied load is constant; no pulse sequence or current ramp is defined.

The positive solid diffusivity is listed as a *defined* parameter because the current cathode expression incorrectly references `Ds_neg`.

## Known issues in the supplied snapshot

### Confirmed source and reference-file inconsistencies

| Location | Finding | Required action |
| --- | --- | --- |
| Class declaration | `test6_compact` differs from filename `BatteryHomog.java` | Make the public class and filename agree. |
| `MODEL_PATH` | Absolute author-specific path | Configure a valid local path. |
| Cathode `Ds_eff` | Uses `Ds_neg*cf_s_pos`; `Ds_pos` is defined but not used here | Use `Ds_pos*cf_s_pos` if distinct electrode diffusivities are intended. Also update `Cathode.txt`. |
| `V_tot_neg` | Defined as `V_s_pos+V_l_pos` | Use the negative-electrode volumes. Equal current values hide the error. Update `ParametersH.txt` too. |
| Scale ratio | Global `epsilon_neg/pos` use `L/l`, while local `epsilon` uses `l/L` | Adopt and document one convention. Values are currently `20` versus `0.05`. These variables are not explicitly used in the shown PDE expressions. |
| `Jps` | Java includes a factor `0.95` absent from `ParametersH.txt` | Choose the intended capacity convention and synchronize the files. |
| Closure factors | Java uses `intop(...)/(l^3)*1[m]`; electrode text files contain only `intop(...)` | Resolve the normalization and unit convention, then synchronize. |
| `Anode.txt` | The third field after `cs_init 0.05*cs_max_neg` is `0.95*cs_max_neg` | Clarify/remove this misleading description field; it is not a second initial value. |
| `ParametersH.txt` | `f_init`, `f_step`, and `f_final` are absent from the Java parameters | Identify these as legacy reference entries or implement their intended use. |

### Physical and numerical issues requiring resolution

1. **Corrector units and effective-coefficient normalization.** The closure fields are not explicitly assigned length units, despite terms such as `1+d(chil3,z)`. For a dimensional cell coordinate and dimensional corrector, a consistent full-cell factor is `intop(1+d(chi3,z))/V_cell`, which is dimensionless. Multiplying this by `1[m]` gives it length units. Conversely, a dimensionless corrector differentiated with respect to dimensional `z` makes the sum with `1` inconsistent. Select a fully dimensional or fully nondimensional formulation and implement it consistently. Do not treat the extra `1[m]` as a general unit fix. Confirm that `V_cell=l^3` agrees with the imported geometry.

2. **Equilibrium-potential units and range.** The interpolation output units are not explicitly set to volts. Define dimensionless arguments and voltage outputs. `Eq_pos` ends at stoichiometry `0.975`, but the positive electrode starts at `0.99`; its initial potential therefore depends on extrapolation. Supply justified data over the operating range or choose a supported initial state.

3. **Initial overpotentials.** With `ps_neg=0`, `pl=0`, and `ps_pos=Eq_pos(0.99)-Eq_neg(0.05)`, both initial overpotentials equal `-Eq_neg(0.05)=-0.313705 V`. This is not an equilibrium initialization. If equilibrium is intended with the negative solid grounded, a consistent electrolyte guess is `pl_init=-Eq_neg(0.05)` throughout the cell. A loaded initial state still requires a consistent algebraic solve.

4. **Explicit time coefficients.** Liquid charge sets `da=0`, but solid charge does not explicitly set `da`; its behavior therefore depends on the interface defaults. For quasistatic solid charge, explicitly set `da=0` and verify `ea=0`. Solid mass also leaves `da` implicit. If `cs` is an intrinsic solid-phase concentration, check whether its accumulation should be weighted by `1-eta` when transport and reaction terms are expressed per total volume. Document the concentration convention before changing this coefficient.

5. **Geometry, volume, and current scaling.** The supplied phase volumes sum to `2.5e-15 m^3`, whereas `A_cell_neg*L_neg=1.5528e-15 m^3`; the ratio is approximately `1.610`. If `A_cell` is the macroscopic cross-section, these values disagree. If it denotes a different area, document it and derive the current conversion. The source gives `Jps≈31.508 A/m^2` and the applied `J≈26.473 A/m^2`. The factor `0.95` appears both in current and duration, producing a product proportional to `0.95^2` relative to the unscaled reference. This needs a stated capacity convention, not an automatic correction.

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

- [COMSOL API overview](https://www.comsol.com/support/learning-center/article/overview-of-the-comsol-api-107912)
- [General Form PDE](https://doc.comsol.com/6.3/doc/com.comsol.help.comsol/comsol_ref_equationbased.32.008.html)
- [Integration operators](https://doc.comsol.com/6.3/doc/com.comsol.help.comsol/comsol_ref_definitions.21.096.html)
- [Function API and interpolation settings](https://doc.comsol.com/6.3/doc/com.comsol.help.comsol/comsol_api_general.47.34.html)
- [Geometry selections](https://doc.comsol.com/6.4/doc/com.comsol.help.comsol/comsol_api_geom.48.060.html)

Documentation versions above are references, not a claim that this model was tested with those releases.

## License and citation

No license or publication citation is supplied in this snapshot. The repository owner should add an appropriate `LICENSE` file and the correct publication reference before release. A COMSOL license is required independently of any license chosen for this source code.
