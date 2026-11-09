package com.pocketproof.content

import com.pocketproof.engine.Sort
import com.pocketproof.engine.SortEnv

private fun world(
    r: Registry,
    id: String,
    title: String,
    topic: String,
    ref: String,
    color: Int,
    intro: String,
    env: SortEnv = SortEnv(),
    body: WorldBuilder.() -> Unit,
): World {
    val b = WorldBuilder(r, id, env)
    b.body()
    val de = GermanText.worlds[id]
    return World(id, title, topic, ref, intro, color, b.levels, de?.title.orEmpty(), de?.topic.orEmpty(), de?.ref.orEmpty(), de?.intro.orEmpty())
}

fun buildWorlds(r: Registry): List<World> = listOf(
    axioms(r), subspaces(r), independence(r), bases(r), maps(r), matrices(r), permutations(r), determinants(r),
)

private fun axioms(r: Registry) = world(
    r, "axioms", "The Axiom Forge", "Vector spaces", "Havlicek §2.2", 0,
    "V is a vector space over a field K: (V, +) is a group, and scalars act on vectors through four axioms. " +
        "Letters a, b, u are vectors; x, y, z are scalars. Commutativity of + is deliberately not assumed: you will prove it yourself.",
) {
    axiom("V4", "One", "1*a = a", "Axiom 4", "Def 2.2.2")
    axiom("N_r", "Neutral right", "a + o = a", "Neutrales Element", "§1.9")
    axiom("N_l", "Neutral left", "o + a = a", "Neutrales Element", "§1.9")
    axiom("Inv_r", "Inverse right", "a + (-a) = o", "Inverses Element", "§1.9")
    axiom("Inv_l", "Inverse left", "(-a) + a = o", "Inverses Element", "§1.9")
    axiom("Assoc", "Associativity", "(a + b) + u = a + (b + u)", "Assoziativität", "§1.9")
    axiom("V1", "Distribute vectors", "x*(a + b) = x*a + x*b", "Axiom 1", "Def 2.2.2")
    axiom("V2", "Distribute scalars", "(x + y)*a = x*a + y*a", "Axiom 2", "Def 2.2.2")
    axiom("V3", "Pull out scalars", "(x*y)*a = x*(y*a)", "Axiom 3", "Def 2.2.2")
    field("K_add0", "x + 0", "x + 0 = x")
    field("K_0add", "0 + x", "0 + x = x")
    field("K_neg", "x + (−x)", "x + (-x) = 0")
    field("K_inv", "Inverse in K", "x != 0 -> x^-1*x = 1")
    field("K_em", "Zero or not", "x = 0 | x != 0")
    field("K_mul1", "x·1", "x*1 = x")
    field("K_1mul", "1·x", "1*x = x")
    field("K_mul0", "x·0", "x*0 = 0")
    field("K_mulassoc", "Assoc. in K", "(x*y)*z = x*(y*z)")
    field("K_nzd", "No zero divisors", "x*y = 0 -> x = 0 | y = 0")
    field("K_one_ne", "1 ≠ 0", "1 != 0")
    field("K_zero_ne", "0 ≠ 1", "0 != 1")
    field("K_sq1", "Square is one", "x*x = 1 -> x = 1 | x = -1")
    field("K_eq_inv", "Solve for inverse", "y != 0 -> x*y = 1 -> x = y^-1")

    level("first", "First Move", "1*a = a",
        "Welcome! Your goal is in the blue box. Drag the card “One” up from the tray and drop it on the left side of the goal.",
        "rw V4", grants = "V4", theorem = false, hint = "Drag the card upward and drop it on 1·a.")
    level("second", "Pick Your Spot", "1*(1*a) = a",
        "Now the rule fits in two places. Either works: try dropping on the inner or the outer product. Highlights show every place a tool fits.",
        "rw V4; rw V4", theorem = false)
    level("neutral", "Nothing Added", "x*(a + o) = x*a",
        "Rules work deep inside a term. Drop “Neutral right” on a + o.",
        "rw N_r", grants = "N_r", theorem = false)
    level("twice", "Double Nothing", "(a + o) + o = a",
        "Brackets matter here, and they are always shown.",
        "rw N_r; rw N_r", theorem = false)
    level("dist", "Multiply Out", "(x + y)*(a + b) = (x*a + y*a) + (x*b + y*b)",
        "Both distributive laws are in your tray. The order you use them in decides the shape you get, and only one shape matches.",
        "rw V1; rw V2; rw V2", grants = "V1,V2", theorem = false,
        hint = "Open up the vector sum first, then each scalar sum.")
    level("add_neg_cancel", "Add and Take Away", "(a + b) + (-b) = a",
        "Regroup, cancel, tidy up. This one joins your tools when you finish: that is how the whole game works.",
        "rw Assoc; rw Inv_r; rw N_r", grants = "Assoc,Inv_r", german = "Kürzen, Vorbereitung")
    level("neg_add_cancel", "Take Away and Add", "(-a) + (a + b) = b",
        "The same idea from the left. Tools are one-way by default: tap the {flip} on a card to use it right-to-left.",
        "rw< Assoc; rw Inv_l; rw N_l", grants = "Inv_l,N_l",
        hint = "Flip Associativity with {flip} to regroup as ((−a) + a) + b.", german = "Kürzen, Vorbereitung")
    level("cancel_r", "Cancel on the Right", "a + u = b + u -> a = b",
        "An implication! Tap Intro to assume the left part; it appears under Given and becomes a tool. Then turn a into (a + u) + (−u): flip “Add and Take Away”, drop it on a, and pick u.",
        "intro; rw< add_neg_cancel | @a | b:=u; rw h1; rw add_neg_cancel",
        german = "Kürzungsregel", ref = "§1.9")
    level("cancel_l", "Cancel on the Left", "u + a = u + b -> a = b",
        "Mirror image of the previous level.",
        "intro; rw< neg_add_cancel | @a | a:=u; rw h1; rw neg_add_cancel",
        german = "Kürzungsregel", ref = "§1.9")
    level("idem", "Only Zero Doubles to Itself", "a + a = a -> a = o",
        "Drop a tool on the whole goal to prove it backwards: “to get this, it is enough to show that”. Use Cancel on the Right with u := a.",
        "intro; apply cancel_r | u:=a; rw N_l",
        hint = "After applying the cancel rule you need a + a = o + a. Simplify the right side.")
    level("zero_smul", "Zero Times Anything", "0*a = o",
        "Equation (2.3) from the book: 0a = (0 + 0)a = 0a + 0a, therefore 0a = o. Use your doubling lemma.",
        "apply idem; rw< V2; rw K_add0", grants = "K_add0", ref = "(2.3)", german = "0a = o")
    level("smul_zero", "Anything Times Zero", "x*o = o",
        "Equation (2.5): xo = x(o + o) = xo + xo.",
        "apply idem; rw< V1; rw N_r", ref = "(2.5)", german = "xo = o")
    level("neg_unique", "Inverses Are Unique", "a + b = o -> b = -a",
        "If b undoes a, then b is the inverse of a.",
        "intro; apply cancel_l | u:=a; rw Inv_r", german = "Eindeutigkeit des Inversen", ref = "§1.9")
    level("eq_neg", "Move Across", "a + b = o -> a = -b",
        "The same from the other side.",
        "intro; apply cancel_r | u:=b; rw Inv_l", ref = "§1.9")
    level("sub_eq_zero", "Difference Zero", "a + (-b) = o -> a = b",
        "When the difference vanishes, the vectors agree.",
        "intro; apply cancel_r | u:=-b; rw Inv_r", ref = "§1.9")
    level("neg_neg", "Double Negative", "-(-a) = a",
        "Cancel −a from the left of both sides.",
        "apply cancel_l | u:=-a; rw Inv_r; rw Inv_l", ref = "§1.9", german = "−(−a) = a")
    level("neg_one", "Minus One", "(-1)*a = -a",
        "Equation (2.4). Apply “Inverses Are Unique”, then write a as 1·a by flipping One.",
        "apply neg_unique; rw< V4 | @a; rw< V2; rw K_neg; rw zero_smul",
        grants = "K_neg", ref = "(2.4)", german = "(−1)a = −a")
    level("smul_neg", "Scalar of a Negative", "x*(-a) = -(x*a)",
        "Uniqueness of inverses again.",
        "apply neg_unique; rw< V1; rw Inv_r; rw smul_zero", ref = "§2.2.4")
    level("neg_smul", "Negative Scalar", "(-x)*a = -(x*a)",
        "And once more, on the scalar side.",
        "apply neg_unique; rw< V2; rw K_neg; rw zero_smul", ref = "§2.2.4")
    level("no_zero_div", "No Zero Divisors", "x*a = o -> x = 0 | a = o",
        "Equation (2.6). Either x = 0 or x is invertible. Drag “Zero or not” into Given, pick x, then tap Cases. In the second case write a as (x⁻¹x)a.",
        "intro; have K_em | x:=x; cases h2; left; right; rw< V4 | @a; rw< K_inv | @1 | x:=x; rw V3; rw h1; rw smul_zero",
        grants = "K_em,K_inv,V3", ref = "(2.6)", german = "xa = o ⇒ x = 0 oder a = o",
        hint = "For x ≠ 0: a = 1a = (x⁻¹x)a = x⁻¹(xa) = x⁻¹o = o.")
    level("double", "Doubling", "(1 + 1)*a = a + a",
        "A warm-up for the boss.",
        "rw V2; rw V4; rw V4", ref = "§2.2.4")
    level("add_comm", "BOSS: Commutativity", "a + b = b + a",
        "Equation (2.7): vector addition commutes, and it follows from the other axioms! Cancel a on the left and b on the right, then compute (1 + 1)(a + b) in two ways.",
        "apply cancel_l | u:=a; apply cancel_r | u:=b; rw< Assoc | @a + (a + b); rw Assoc | @((a + a) + b) + b; rw< Assoc | @a + (b + a); rw Assoc | @((a + b) + a) + b; rw< double | @a + a; rw< double | @b + b; rw< V1; rw double",
        boss = true, ref = "(2.7), Satz 2.2.5", german = "(V, +) ist kommutativ",
        hint = "Reach (a + a) + (b + b) = (a + b) + (a + b). Then both sides are (1 + 1)(a + b).")
}

private fun subspaces(r: Registry) = world(
    r, "subspaces", "Subspace Caverns", "Subspaces", "Havlicek §2.3", 1,
    "You earned {compute} Compute: once commutativity is proved, it closes any equation that is just vector-space arithmetic. " +
        "A subset U is a subspace (written U ≤ V) exactly when it is non-empty and a + x·b stays in U. That is the Unterraumkriterium 2.3.2.",
) {
    axiom("Sub_crit", "Subspace criterion", "Sub(U) <-> (exists a, a in U) & (forall a b x, a in U -> b in U -> a + x*b in U)", "Unterraumkriterium", "Satz 2.3.2")
    definition("mem_Z", "In {o}", "a in Z <-> a = o", "Nullraum", "Bsp 2.3.3")
    definition("mem_V", "In V", "a in V", "Ganzer Raum", "Bsp 2.3.3")
    definition("mem_line", "In [u]", "b in line(u) <-> exists x, b = x*u", "Hülle eines Vektors", "Bsp 2.3.3")
    definition("mem_cap", "In U ∩ W", "a in U cap W <-> a in U & a in W", "Durchschnitt", "§2.3")
    definition("mem_sum", "In U + W", "a in U + W <-> exists u w, u in U & w in W & a = u + w", "Summe von Unterräumen", "§2.8")
    definition("mem_span", "In the span", "a in span(m) <-> exists c, a = lc(c,m)", "Hülle", "§2.3.5")
    definition("sub_def", "Subset", "U sub W <-> forall a, a in U -> a in W", "Teilmenge", "§1.2")
    definition("set_ext", "Set equality", "U = W <-> U sub W & W sub U", "Mengengleichheit", "§1.2")
    axiom("lc_add", "Σ of a sum", "lc(c + d, m) = lc(c,m) + lc(d,m)", "Linearkombination", "§2.3.4")
    axiom("lc_smul", "Σ of a multiple", "lc(x*c, m) = x*lc(c,m)", "Linearkombination", "§2.3.4")
    axiom("lc_zero", "Trivial Σ", "lc(zc, m) = o", "Triviale Linearkombination", "§2.4.3")
    axiom("lc_delta", "Σ picks a vector", "lc(delta(i), m) = m[i]", "Kronecker-Symbol", "Bsp 2.3.7")
    lecture("lc_mem", "Σ stays in U", "Sub(U) -> (forall i, m[i] in U) -> lc(c,m) in U", "Induktion über die Summanden", "§2.3")

    level("self_sub", "Vector Minus Itself", "a + (-1)*a = o",
        "Your new power: tap {compute} Compute. It only closes goals that really are arithmetic.",
        "compute")
    level("sub_closed", "The Criterion at Work", "Sub(U) -> a in U -> b in U -> a + x*b in U",
        "Drop the criterion on the hypothesis U ≤ V to unpack it. And-statements and “there exists” split up automatically.",
        "intro; rw Sub_crit | in h1; apply h4", grants = "Sub_crit", ref = "Satz 2.3.2")
    level("sub_zero", "Zero Is Inside", "Sub(U) -> o in U",
        "U has some element a, and o = a + (−1)·a. Flip “Vector Minus Itself”, drop it on o, and choose a.",
        "intro; rw Sub_crit | in h1; rw< self_sub | @o | a:=a; apply h2", ref = "§2.3")
    level("sub_add", "Closed Under Sums", "Sub(U) -> a in U -> b in U -> a + b in U",
        "Dress b up as 1·b, so that the criterion fits.",
        "intro; rw< V4 | @b; apply sub_closed", ref = "§2.3")
    level("sub_smul", "Closed Under Multiples", "Sub(U) -> b in U -> x*b in U",
        "x·b = o + x·b. As in the book's proof of 2.3.2.",
        "intro; rw< N_l | @x*b; apply sub_closed; apply sub_zero", ref = "Satz 2.3.2")
    level("sub_neg", "Closed Under Negatives", "Sub(U) -> a in U -> -a in U",
        "A short one.",
        "intro; rw< neg_one | @-a; apply sub_smul", ref = "§2.3")
    level("triv_sub", "The Smallest Subspace", "Sub(Z)",
        "{o} is a subspace. When a goal is “A ∧ B”, tap Split. Choose a witness for ∃ with Choose.",
        "rw Sub_crit; split; use o; rw mem_Z; intro; rw mem_Z | in h1; rw mem_Z | in h2; rw mem_Z; rw h1; rw h2; compute",
        grants = "mem_Z", ref = "Bsp 2.3.3 (4)", german = "Nullraum")
    level("univ_sub", "The Largest Subspace", "Sub(V)",
        "V is a subspace of itself.",
        "rw Sub_crit; split; use o; apply mem_V; intro; apply mem_V",
        grants = "mem_V", ref = "Bsp 2.3.3 (4)")
    level("line_sub", "A Line Through o", "Sub(line(u))",
        "Example 2.3.3 (1): all multiples of u. For the sum, the witness is x′ + x·x′′.",
        "rw Sub_crit; split; use o; rw mem_line; use 0; compute; intro; rw mem_line | in h1; rw mem_line | in h2; rw mem_line; use x' + x*x''; rw h1 | @a; rw h2 | @b; compute",
        grants = "mem_line", offers = "x' + x*x''; x' + x''; x*x''", ref = "Bsp 2.3.3 (1)")
    level("cap_sub", "Intersections", "Sub(U) -> Sub(W) -> Sub(U cap W)",
        "Satz 2.3.8 for two subspaces.",
        "intro; rw Sub_crit; split; use o; rw mem_cap; split; apply sub_zero; apply sub_zero; intro; rw mem_cap | in h3; rw mem_cap | in h4; rw mem_cap; split; apply sub_closed; apply sub_closed",
        grants = "mem_cap", ref = "Satz 2.3.8", german = "Durchschnitt von Unterräumen")
    level("sum_sub", "Sums of Subspaces", "Sub(U) -> Sub(W) -> Sub(U + W)",
        "Split a and b into their U-part and W-part, then recombine.",
        "intro; rw Sub_crit; split; use o; rw mem_sum; use o; use o; split; apply sub_zero; split; apply sub_zero; compute; intro; rw mem_sum | in h3; rw mem_sum | in h4; rw mem_sum; use u + x*u'; use w + x*w'; split; apply sub_closed; split; apply sub_closed; rw h6 | @a; rw h8 | @b; compute",
        grants = "mem_sum", offers = "u + x*u'; w + x*w'; u + u'; w + w'", ref = "§2.8", german = "Summe von Unterräumen")
    level("mem_self", "Each Vector Lies in the Span", "m[i] in span(m)",
        "Linear combinations are written Σ cᵢmᵢ. The Kronecker family δᵢ picks out a single vector.",
        "rw mem_span; use delta(i); rw lc_delta", grants = "mem_span,lc_delta", offers = "delta(i)", ref = "§2.3.5")
    level("span_sub", "BOSS: The Span Is a Subspace", "Sub(span(m))",
        "Satz 2.3.6. The witness is the family c + x·c′.",
        "rw Sub_crit; split; use o; rw mem_span; use zc; rw lc_zero; intro; rw mem_span | in h1; rw mem_span | in h2; rw mem_span; use c + x*c'; rw lc_add; rw lc_smul; rw h1 | @a; rw h2 | @b",
        grants = "lc_add,lc_smul,lc_zero", offers = "c + x*c'; c + c'; x*c'", boss = true, ref = "Satz 2.3.6", german = "Hülle ist Unterraum")
    level("span_min", "The Span Is Smallest", "Sub(U) -> (forall i, m[i] in U) -> span(m) sub U",
        "Any subspace containing all mᵢ contains their span.",
        "intro; rw sub_def; intro; rw mem_span | in h3; rw h3 | @a; apply lc_mem",
        grants = "sub_def,lc_mem", ref = "Satz 2.3.9")
    level("span_eq", "Pinning Down a Span", "Sub(U) -> (forall i, m[i] in U) -> (forall a, a in U -> a in span(m)) -> U = span(m)",
        "Two sets are equal when each contains the other.",
        "intro; rw set_ext; split; rw sub_def; apply span_min", grants = "set_ext", ref = "Satz 2.3.9")
}

private fun independence(r: Registry) = world(
    r, "independence", "Independence Isles", "Linear independence", "Havlicek §2.4", 2,
    "A family (mᵢ) is linearly independent when only the trivial combination gives o. That is Satz 2.4.5; c, d denote coefficient families and 𝟎 the zero family.",
) {
    definition("Indep_def", "Independence", "Indep(m) <-> forall c, lc(c,m) = o -> c = zc", "Linear unabhängig", "Satz 2.4.5")
    definition("Indep1_def", "One vector indep.", "Indep1(a) <-> forall x, x*a = o -> x = 0", "Linear unabhängig", "Bsp 2.4.2")
    definition("Indep2_def", "Two vectors indep.", "Indep2(a,b) <-> forall x y, x*a + y*b = o -> x = 0 & y = 0", "Linear unabhängig", "Satz 2.4.5")
    axiom("lc_neg", "Σ of a negative", "lc(-c, m) = -lc(c,m)", "Linearkombination", "§2.3.4")
    axiom("coef_cancel", "Coefficient difference", "c + (-d) = zc -> c = d", "Rechnen mit Koeffizienten", "§2.3.4")
    axiom("delta_ne", "δ is not zero", "delta(i) != zc", "Kronecker-Symbol", "Bsp 2.3.7")
    axiom("delta_ne2", "Distinct δ differ", "i != j -> delta(i) + (-delta(j)) != zc", "Kronecker-Symbol", "Bsp 2.3.7")
    axiom("lc_restrict", "Σ over a subfamily", "lc(c, restrict(m,J)) = lc(ext(c,J), m)", "Teilfamilie", "A 2.4.4")
    axiom("ext_zero", "Extended by zeros", "ext(c,J) = zc -> c = zc", "Teilfamilie", "A 2.4.4")

    level("triv_lc", "The Trivial Combination", "exists c, lc(c,m) = o",
        "Every family has one.", "use zc; rw lc_zero", ref = "§2.4.3", german = "Triviale Linearkombination")
    level("indep_unique", "Coefficients Are Unique", "Indep(m) -> lc(c,m) = lc(d,m) -> c = d",
        "The uniqueness half of Satz 2.5.3: consider the difference c − d.",
        "intro; apply coef_cancel; rw Indep_def | in h1; apply h1; rw lc_add; rw lc_neg; rw h2; compute",
        grants = "Indep_def,lc_neg,coef_cancel", ref = "Satz 2.5.3")
    level("single_indep", "A Single Vector", "a != o -> Indep1(a)",
        "Example 2.4.2 (3): one non-zero vector is independent. No Zero Divisors does the work. Drop it on a hypothesis to reason forward.",
        "intro; rw Indep1_def; intro; fwd h2 no_zero_div; cases h3; fwd h5 h1",
        grants = "Indep1_def", ref = "Bsp 2.4.2 (3)")
    level("pair_first", "Partners Are Non-Zero", "Indep2(a,b) -> a != o",
        "Assume a = o, then feed x := 1, y := 0 to independence. A hypothesis with ∀ can be dragged into Given to instantiate it.",
        "intro; rw Indep2_def | in h1; have h1 | x:=1 | y:=0; fwd h3 K_one_ne; rw h2; compute",
        grants = "Indep2_def,K_one_ne", ref = "Bsp 2.4.2 (2)")
    level("indep_zero_vec", "A Zero Vector Spoils It", "m[j] = o -> ~Indep(m)",
        "Example 2.4.2 (2).",
        "intro; rw Indep_def | in h2; have lc_delta | i:=j | m:=m; rw h1 | in h3; fwd h3 h2; fwd h4 delta_ne",
        grants = "delta_ne", ref = "Bsp 2.4.2 (2)")
    level("indep_no_repeat", "No Repeats", "i != j -> m[i] = m[j] -> ~Indep(m)",
        "Example 2.4.2 (1): non-injective families are dependent. Use the coefficients δᵢ − δⱼ.",
        "intro; rw Indep_def | in h3; have h3 | c:=delta(i) + (-delta(j)); fwd h4 delta_ne2; rw lc_add; rw lc_neg; rw lc_delta; rw lc_delta; rw h2; compute",
        grants = "delta_ne2", offers = "delta(i) + (-delta(j)); delta(i); delta(j)", ref = "Bsp 2.4.2 (1)")
    level("dep_line", "Dependent Means On a Line", "x != 0 -> x*a + y*b = o -> a in line(b)",
        "Satz 2.4.4 for two vectors: solve for a, as the book does by dividing by x.",
        "intro; rw mem_line; use -(x^-1*y); fwd h2 eq_neg; rw< V4 | @a; rw< K_inv | @1 | x:=x; rw V3; rw h3; compute",
        offers = "-(x^-1*y); x^-1*y; -y", ref = "Satz 2.4.4")
    level("indep_restrict", "BOSS: Subfamilies Stay Independent", "Indep(m) -> Indep(restrict(m,J))",
        "A 2.4.4 (a). Extend the coefficients by zeros and use independence of the big family.",
        "intro; rw Indep_def; rw Indep_def | in h1; intro; apply ext_zero | J:=J; apply h1; rw< lc_restrict",
        grants = "lc_restrict,ext_zero", boss = true, ref = "A 2.4.4 (a)", german = "Teilfamilie ist linear unabhängig")
}

private fun bases(r: Registry) = world(
    r, "bases", "Basis Camp", "Bases and dimension", "Havlicek §2.5–2.6", 3,
    "A basis is an independent family that spans V. The Austauschsatz of Steinitz is proved by an induction in the book, and you receive it as a tool.",
) {
    definition("Basis_def", "Basis", "Basis(m) <-> Indep(m) & Gen(m)", "Basis", "§2.5.1")
    definition("Gen_def", "Spanning", "Gen(m) <-> forall a, a in span(m)", "Erzeugendensystem", "§2.5.1")
    definition("Gen2_def", "Two vectors span", "Gen2(a,b) <-> forall v, exists r s, v = r*a + s*b", "Erzeugendensystem", "§2.5.1")
    lecture("Steinitz", "Steinitz", "Indep(m) -> Gen(n) -> card(m) <= card(n)", "Austauschsatz von Steinitz", "Satz 2.6.3")
    lecture("exists_basis", "A basis exists", "exists B, Basis(B)", "Existenz einer Basis", "Satz 2.5.6")
    lecture("dim_card", "Dimension", "Basis(m) -> dim(V) = card(m)", "Dimension", "Def 2.6.5")
    axiom("le_antisymm", "Antisymmetry", "k <= l -> l <= k -> k = l", "Antisymmetrie", "§1.8")

    level("basis_indep", "A Basis Is Independent", "Basis(m) -> Indep(m)",
        "Unpack the definition.", "intro; rw Basis_def | in h1", grants = "Basis_def", ref = "§2.5.1")
    level("basis_gen", "A Basis Spans", "Basis(m) -> Gen(m)",
        "Likewise.", "intro; rw Basis_def | in h1", ref = "§2.5.1")
    level("basis_rep", "Every Vector Has Coordinates", "Basis(m) -> exists c, a = lc(c,m)",
        "The existence half of Satz 2.5.3.",
        "intro; fwd h1 basis_gen; rw Gen_def | in h2; have h2 | a:=a; rw mem_span | in h3; use c",
        grants = "Gen_def", ref = "Satz 2.5.3")
    level("basis_unique", "Coordinates Are Unique", "Basis(m) -> lc(c,m) = lc(d,m) -> c = d",
        "The other half of Satz 2.5.3.",
        "intro; apply indep_unique | m:=m; apply basis_indep", ref = "Satz 2.5.3", german = "Eindeutige Darstellung")
    level("basis_same", "All Bases Have the Same Size", "Basis(m) -> Basis(n) -> card(m) = card(n)",
        "Satz 2.6.4: use Steinitz in both directions.",
        "intro; apply le_antisymm; apply Steinitz; apply basis_indep; apply basis_gen; apply Steinitz; apply basis_indep; apply basis_gen",
        grants = "Steinitz,le_antisymm", ref = "Satz 2.6.4", german = "Basen sind gleichmächtig")
    level("indep_le_dim", "Independent Means Small", "Indep(n) -> card(n) <= dim(V)",
        "Satz 2.6.6. Bring in a basis first by dragging “A basis exists” into Given.",
        "intro; have exists_basis; rw dim_card | m:=B; apply Steinitz; apply basis_gen",
        grants = "exists_basis,dim_card", ref = "Satz 2.6.6")
    level("gen_ge_dim", "Spanning Means Large", "Gen(n) -> dim(V) <= card(n)",
        "The other half of Satz 2.6.6.",
        "intro; have exists_basis; rw dim_card | m:=B; apply Steinitz; apply basis_indep", ref = "Satz 2.6.6")
    level("exchange2", "BOSS: The Exchange Lemma", "Gen2(a,b) -> x != 0 -> Gen2(x*a + y*b, b)",
        "Austauschlemma 2.6.2 for two vectors: a can be swapped for x·a + y·b, because a = x⁻¹((xa + yb) − yb). Compute knows x⁻¹x = 1 when x ≠ 0 is given.",
        "intro; rw Gen2_def; rw Gen2_def | in h1; intro; have h1 | v:=v; use r*x^-1; use s + (-(r*x^-1*y)); rw h3 | @v; compute",
        grants = "Gen2_def", offers = "r*x^-1; s + (-(r*x^-1*y)); r*x; s + (-y); r + s",
        boss = true, ref = "Satz 2.6.2", german = "Austauschlemma")
}

private fun maps(r: Registry) = world(
    r, "maps", "The Map Room", "Linear maps", "Havlicek §3.2", 4,
    "A map f : V → W is linear when it respects + and scalar multiples (Def 3.2.1). Kernel, image and rank all live here.",
) {
    definition("Lin_def", "Linear", "Lin(f) <-> (forall a b, f(a + b) = f(a) + f(b)) & (forall x a, f(x*a) = x*f(a))", "Linear", "Def 3.2.1")
    definition("comp_apply", "Composition", "(g∘f)(a) = g(f(a))", "Produkt von Abbildungen", "§1.5")
    definition("mem_ker", "In the kernel", "a in ker(f) <-> f(a) = o", "Kern", "§3.2.1")
    definition("mem_im", "In the image", "b in im(f) <-> exists a, b = f(a)", "Bild", "§3.2")
    definition("mem_pre", "In the preimage", "a in preimg(f,T) <-> f(a) in T", "Urbild", "Satz 3.2.4")
    definition("Inj_def", "Injective", "Inj(f) <-> forall a b, f(a) = f(b) -> a = b", "Injektiv", "§1.4")
    axiom("f_inv", "f after f⁻¹", "Bij(f) -> f(f^-1(b)) = b", "Inverse Abbildung", "§1.5")
    axiom("inv_f", "f⁻¹ after f", "Bij(f) -> f^-1(f(a)) = a", "Inverse Abbildung", "§1.5")
    axiom("map_lc", "f of a Σ", "Lin(f) -> f(lc(c,m)) = lc(c, mapFam(f,m))", "Linearkombination", "Satz 3.2.4")
    lecture("rangformel", "Rank formula", "Lin(f) -> rk(f) + defect(f) = dimV", "Rangformel", "Satz 3.2.8")
    lecture("inj_def0", "Injective: defect 0", "Lin(f) -> Inj(f) -> defect(f) = n0", "Defekt", "§3.2.7")
    lecture("surj_rk", "Full rank: surjective", "Lin(f) -> rk(f) = dimW -> Surj(f)", "Rang", "Satz 3.2.6")
    axiom("n_add0", "k + 0", "k + n0 = k", "Natürliche Zahlen", "")

    level("map_add", "Sums Go Through", "Lin(f) -> f(a + b) = f(a) + f(b)",
        "Unpack linearity, then apply.", "intro; rw Lin_def | in h1; apply h1", grants = "Lin_def", ref = "Def 3.2.1")
    level("map_smul", "Scalars Go Through", "Lin(f) -> f(x*a) = x*f(a)",
        "The second axiom.", "intro; rw Lin_def | in h1; apply h2", ref = "Def 3.2.1")
    level("map_zero", "Zero Maps to Zero", "Lin(f) -> f(o) = o",
        "f(o) doubles to itself.", "intro; apply idem; rw< map_add; rw N_r", ref = "§3.2.1")
    level("map_neg", "Negatives Go Through", "Lin(f) -> f(-a) = -f(a)",
        "Uniqueness of inverses, in W.", "intro; apply neg_unique; rw< map_add; rw Inv_r; apply map_zero", ref = "§3.2.1")
    level("map_comb", "One Move, Both Rules", "Lin(f) -> f(a + x*b) = f(a) + x*f(b)",
        "Formula (3.1).", "intro; rw map_add; rw map_smul", ref = "Satz 3.2.2")
    level("crit_zero", "Criterion Fixes Zero", "(forall a b x, f(a + x*b) = f(a) + x*f(b)) -> f(o) = o",
        "Instantiate the hypothesis with a := o, b := o, x := 1 by dragging it into Given.",
        "intro; have h1 | a:=o | b:=o | x:=1; rw V4 | in h2 | @1*o; rw V4 | in h2 | @1*f(o); rw N_r | in h2; apply idem; rw< h2", ref = "Satz 3.2.2")
    level("lin_crit", "The Linearity Criterion", "(forall a b x, f(a + x*b) = f(a) + x*f(b)) -> Lin(f)",
        "Satz 3.2.2: take x := 1, then a := o.",
        "intro; rw Lin_def; split; intro; rw< V4 | @b; rw h1; compute; intro; rw< N_l | @x*a; rw h1; rw crit_zero; rw N_l", ref = "Satz 3.2.2", german = "Linearitätskriterium")
    level("comp_lin", "Composites Are Linear", "Lin(f) -> Lin(g) -> Lin(g∘f)",
        "Stated after Satz 3.2.4.",
        "intro; rw Lin_def; split; intro; rw comp_apply; rw comp_apply; rw comp_apply; rw map_add; rw map_add; intro; rw comp_apply; rw comp_apply; rw map_smul; rw map_smul",
        grants = "comp_apply", ref = "§3.2", german = "Produkt linearer Abbildungen")
    level("ker_sub", "The Kernel Is a Subspace", "Lin(f) -> Sub(ker(f))",
        "Apply the criterion.",
        "intro; rw Sub_crit; split; use o; rw mem_ker; apply map_zero; intro; rw mem_ker | in h2; rw mem_ker | in h3; rw mem_ker; rw map_comb; rw h2; rw h3; compute",
        grants = "mem_ker", ref = "§3.2", german = "Kern ist Unterraum")
    level("im_sub", "The Image Is a Subspace", "Lin(f) -> Sub(im(f))",
        "Satz 3.2.4 (a) for U = V. Your witness combines the two preimages.",
        "intro; rw Sub_crit; split; use o; rw mem_im; use o; rw map_zero; intro; rw mem_im | in h2; rw mem_im | in h3; rw mem_im; use a' + x*a''; rw map_comb; rw h2 | @a; rw h3 | @b",
        grants = "mem_im", offers = "a' + x*a''; a' + a''; x*a''", ref = "Satz 3.2.4 (a)", german = "Bildraum")
    level("pre_sub", "Preimages Are Subspaces", "Lin(f) -> Sub(T) -> Sub(preimg(f,T))",
        "Satz 3.2.4 (b).",
        "intro; rw Sub_crit; split; use o; rw mem_pre; rw map_zero; apply sub_zero; intro; rw mem_pre | in h3; rw mem_pre | in h4; rw mem_pre; rw map_comb; apply sub_closed",
        grants = "mem_pre", ref = "Satz 3.2.4 (b)", german = "Urbild")
    level("inj_ker", "Injective: Only o in the Kernel", "Lin(f) -> Inj(f) -> f(a) = o -> a = o",
        "One direction of “injective iff ker f = {o}”.",
        "intro; rw Inj_def | in h2; apply h2; rw map_zero", grants = "Inj_def", ref = "§3.2.1")
    level("ker_inj", "Trivial Kernel: Injective", "Lin(f) -> (forall a, f(a) = o -> a = o) -> Inj(f)",
        "The other direction: look at a − b.",
        "intro; rw Inj_def; intro; apply sub_eq_zero; apply h2; rw map_add; rw map_neg; rw h3; rw Inv_r", ref = "§3.2.1")
    level("inj_indep", "Injective Maps Keep Independence", "Lin(f) -> Inj(f) -> Indep(m) -> Indep(mapFam(f,m))",
        "One direction of Satz 3.2.5 (a).",
        "intro; rw Indep_def; rw Indep_def | in h3; intro; apply h3; apply inj_ker | f:=f; rw map_lc",
        grants = "map_lc", ref = "Satz 3.2.5 (a)")
    level("inv_lin", "The Inverse Is Linear", "Lin(f) -> Bij(f) -> Lin(f^-1)",
        "Satz 3.2.4 (d): write a as f(f⁻¹(a)) and b as f(f⁻¹(b)), then pull f out.",
        "intro; apply lin_crit; intro; rw< f_inv | @a | f:=f; rw< f_inv | @b | f:=f; rw< map_comb; rw inv_f",
        grants = "f_inv,inv_f", ref = "Satz 3.2.4 (d)", german = "Umkehrabbildung ist linear")
    level("inj_surj", "BOSS: Injective Implies Surjective", "Lin(f) -> dimV = dimW -> Inj(f) -> Surj(f)",
        "Satz 3.2.9. With equal finite dimensions, the Rangformel forces it.",
        "intro; apply surj_rk; rw< h2; rw< rangformel | @dimV | f:=f; rw inj_def0; rw n_add0",
        grants = "rangformel,inj_def0,surj_rk,n_add0", boss = true, ref = "Satz 3.2.9")
}

private val MATRIX_ENV = SortEnv(mapOf("B" to Sort.Mat))

private fun matrices(r: Registry) = world(
    r, "matrices", "Matrix Mines", "Matrices", "Havlicek §3.3–3.5", 5,
    "Square matrices multiply associatively and E is the identity. Products do not commute, so {compute} Compute cannot help here: every step is yours.",
    MATRIX_ENV,
) {
    axiom("M_assoc", "Associativity", "(A*B)*C = A*(B*C)", "Assoziativität", "§3.4")
    axiom("M_E_r", "A·E", "A*E = A", "Einheitsmatrix", "§3.4")
    axiom("M_E_l", "E·A", "E*A = A", "Einheitsmatrix", "§3.4")
    axiom("inv_l", "A⁻¹·A", "Inv(A) -> A^-1*A = E", "Inverse Matrix", "§3.5")
    axiom("inv_r", "A·A⁻¹", "Inv(A) -> A*A^-1 = E", "Inverse Matrix", "§3.5")
    axiom("inv_mk", "Two-sided inverse", "A*X = E -> X*A = E -> Inv(A)", "Invertierbar", "§3.5")
    axiom("T_mul", "Transpose of product", "(A*B)^T = B^T*A^T", "Transponierte", "§3.4")
    axiom("T_E", "Eᵀ", "E^T = E", "Transponierte", "§3.4")
    axiom("inv_T", "Transpose invertible", "Inv(A) -> Inv(A^T)", "Transponierte", "§3.5")
    axiom("M_dist", "A over a sum", "A*(a + b) = A*a + A*b", "Matrixprodukt", "Satz 3.3.4")
    axiom("M_sm", "A past a scalar", "A*(x*a) = x*(A*a)", "Matrixprodukt", "Satz 3.3.4")
    definition("mem_nul", "In the kernel of A", "a in nul(A) <-> A*a = o", "Kern", "§4.7")

    level("E_unique", "Only One Identity", "(forall X, X*P = X) -> P = E",
        "If P acts like an identity, it is E. Flip E·A and write P as E·P.",
        "intro; rw< M_E_l | @P; rw h1", grants = "M_E_l,M_E_r,M_assoc", ref = "§3.4")
    level("inv_unique", "Left Meets Right", "P*A = E -> A*Q = E -> P = Q",
        "A left inverse and a right inverse must coincide.",
        "intro; rw< M_E_r | @P; rw< h2 | @E; rw< M_assoc; rw h1; rw M_E_l", ref = "§3.5")
    level("right_inv_unique", "Inverses Are Unique", "Inv(A) -> A*X = E -> X = A^-1",
        "Multiply A⁻¹ in from the left.",
        "intro; rw< M_E_l | @X; rw< inv_l | @E | A:=A; rw M_assoc; rw h2; rw M_E_r", grants = "inv_l,inv_r", ref = "§3.5")
    level("cancel_mat", "Cancelling a Matrix", "Inv(A) -> A*X = A*Y -> X = Y",
        "Only invertible matrices may be cancelled.",
        "intro; rw< M_E_l | @X; rw< inv_l | @E | A:=A; rw M_assoc; rw h2; rw< M_assoc; rw inv_l; rw M_E_l", ref = "§3.5")
    level("prod_invertible", "Products of Invertibles", "Inv(A) -> Inv(C) -> Inv(A*C)",
        "Socks and shoes: the inverse is C⁻¹·A⁻¹.",
        "intro; apply inv_mk | X:=C^-1*A^-1; rw M_assoc; rw< M_assoc | @C*(C^-1*A^-1); rw inv_r; rw M_E_l; rw inv_r; rw M_assoc; rw< M_assoc | @A^-1*(A*C); rw inv_l; rw M_E_l; rw inv_l",
        grants = "inv_mk", offers = "C^-1*A^-1; A^-1*C^-1; A*C", ref = "§3.5", german = "GLₙ(K) ist eine Gruppe")
    level("prod_inv", "Socks and Shoes", "Inv(A) -> Inv(C) -> C^-1*A^-1 = (A*C)^-1",
        "Now name the inverse exactly.",
        "intro; apply right_inv_unique; apply prod_invertible; rw M_assoc; rw< M_assoc | @C*(C^-1*A^-1); rw inv_r; rw M_E_l; rw inv_r", ref = "§3.5")
    level("T_inv", "Transpose of an Inverse", "Inv(A) -> (A^-1)^T = (A^T)^-1",
        "Combine the transpose rules with uniqueness of inverses.",
        "intro; apply right_inv_unique; apply inv_T; rw< T_mul; rw inv_l; rw T_E",
        grants = "T_mul,T_E,inv_T", ref = "§4.9")
    level("mat_linear", "Matrices Are Linear", "A*(a + x*b) = A*a + x*(A*b)",
        "The map x ↦ Ax of Satz 3.3.4 is linear.",
        "rw M_dist; rw M_sm", grants = "M_dist,M_sm", ref = "Satz 3.3.4")
    level("mat_zero", "A Kills Zero", "A*o = o", "Doubling, once again.",
        "apply idem; rw< M_dist; rw N_r", ref = "Satz 3.3.4")
    level("nul_sub", "BOSS: Solutions Form a Subspace", "Sub(nul(A))",
        "The solutions of Ax = o are a subspace of Kⁿ.",
        "rw Sub_crit; split; use o; rw mem_nul; rw mat_zero; intro; rw mem_nul | in h1; rw mem_nul | in h2; rw mem_nul; rw mat_linear; rw h1; rw h2; compute",
        grants = "mem_nul", boss = true, ref = "§4.7", german = "Lösungsraum")
}

private fun permutations(r: Registry) = world(
    r, "permutations", "Sign Shrine", "Permutations and the sign", "Havlicek §1.13", 6,
    "Determinants need the sign of a permutation. sgn is a homomorphism Sₙ → {1, −1} (Satz 1.13.7), and transpositions are odd (Satz 1.13.6).",
) {
    axiom("P_assoc", "Associativity", "(σ∘τ)∘ρ = σ∘(τ∘ρ)", "Gruppe Sₙ", "§1.13")
    axiom("P_id_l", "id ∘ σ", "idp∘σ = σ", "Gruppe Sₙ", "§1.13")
    axiom("P_id_r", "σ ∘ id", "σ∘idp = σ", "Gruppe Sₙ", "§1.13")
    axiom("P_inv_l", "σ⁻¹ ∘ σ", "σ^-1∘σ = idp", "Gruppe Sₙ", "§1.13")
    lecture("sgn_hom", "sgn is multiplicative", "sgn(σ∘τ) = sgn(σ)*sgn(τ)", "Signum, Homomorphismus", "Satz 1.13.7")
    lecture("sgn_sq", "sgn squared", "sgn(σ)*sgn(σ) = 1", "sgn σ ∈ {1, −1}", "(1.10)")
    lecture("transp_odd", "Transpositions are odd", "Transp(π) -> sgn(π) = -1", "Transposition", "Satz 1.13.6")
    definition("Even_def", "Even", "Even(σ) <-> sgn(σ) = 1", "Gerade Permutation", "§1.13.4")

    level("sgn_id", "The Identity Is Even", "sgn(idp) = 1",
        "Write id as id ∘ id.", "rw< P_id_l | @idp; rw sgn_hom; rw sgn_sq",
        grants = "P_id_l,sgn_hom,sgn_sq", ref = "Satz 1.13.7")
    level("sgn_inv", "Inverse Has the Same Sign", "sgn(σ^-1) = sgn(σ)",
        "The closing remark of Satz 1.13.7. Multiply by 1, written as sgn σ · sgn σ.",
        "rw< K_mul1 | @sgn(σ^-1); rw< sgn_sq | @1 | σ:=σ; rw< K_mulassoc; rw< sgn_hom; rw P_inv_l; rw sgn_id; rw K_1mul",
        grants = "K_mul1,K_1mul,K_mulassoc,P_inv_l", ref = "Satz 1.13.7")
    level("sgn_comm", "Order Does Not Matter", "sgn(σ∘τ) = sgn(τ∘σ)",
        "Sₙ is not commutative, but signs are numbers.", "rw sgn_hom; rw sgn_hom; compute", ref = "§1.13")
    level("sgn_square", "Squares Are Even", "sgn(σ∘σ) = 1", "Quick one.", "rw sgn_hom; rw sgn_sq", ref = "§1.13")
    level("two_transp", "Two Swaps Are Even", "Transp(π) -> Transp(ρ) -> sgn(π∘ρ) = 1",
        "Two wrongs make a right.", "intro; rw sgn_hom; rw transp_odd | @sgn(π); rw transp_odd | @sgn(ρ); compute",
        grants = "transp_odd", ref = "Satz 1.13.7")
    level("transp_flip", "A Swap Flips the Sign", "Transp(π) -> sgn(π∘σ) = -sgn(σ)",
        "This is exactly why swapping two columns negates a determinant.",
        "intro; rw sgn_hom; rw transp_odd; compute", ref = "Satz 1.13.7")
    level("sgn_conj", "Conjugates Share a Sign", "sgn(σ∘(τ∘σ^-1)) = sgn(τ)",
        "Use “Order Does Not Matter” on the outer composition.",
        "rw sgn_comm; rw P_assoc; rw P_inv_l; rw P_id_r", grants = "P_assoc,P_id_r", ref = "§1.13")
    level("even_comp", "Even Times Even", "Even(σ) -> Even(τ) -> Even(σ∘τ)",
        "The alternating group Aₙ is closed under composition.",
        "intro; rw Even_def; rw Even_def | in h1; rw Even_def | in h2; rw sgn_hom; rw h1; rw h2; compute",
        grants = "Even_def", ref = "§1.13", german = "Alternierende Gruppe")
    level("even_inv", "BOSS: Inverses of Even Permutations", "Even(σ) -> Even(σ^-1)",
        "So Aₙ is a subgroup.", "intro; rw Even_def; rw Even_def | in h1; rw sgn_inv",
        boss = true, ref = "§1.13", german = "Alternierende Gruppe")
}

private fun determinants(r: Registry) = world(
    r, "determinants", "Determinant Summit", "Determinants", "Standard theory, not in your PDF", 7,
    "A determinant form Δ takes columns and returns a scalar. It is linear in each column and zero when two columns agree. " +
        "Δ(a, b) means: a and b sit in two chosen columns, and every other column stays frozen. " +
        "Your scanned PDF stops before this chapter, so these levels use the standard definition, matching the Determinantenform on your exercise sheets.",
    MATRIX_ENV,
) {
    axiom("D_add1", "Add in slot 1", "D(a + u, b) = D(a,b) + D(u,b)", "Multilinear", "Determinantenform")
    axiom("D_sm1", "Scale in slot 1", "D(x*a, b) = x*D(a,b)", "Multilinear", "Determinantenform")
    axiom("D_add2", "Add in slot 2", "D(a, b + u) = D(a,b) + D(a,u)", "Multilinear", "Determinantenform")
    axiom("D_sm2", "Scale in slot 2", "D(a, x*b) = x*D(a,b)", "Multilinear", "Determinantenform")
    axiom("D_alt", "Equal columns", "D(a,a) = 0", "Alternierend", "Determinantenform")
    axiom("det_E", "det E", "det(E) = 1", "Normierung", "Determinante")
    axiom("Dl_apply", "The form X ↦ det AX", "evalF(Dl(A),X) = det(A*X)", "Determinantenform", "Determinante")
    lecture("Dl_form", "X ↦ det AX is a form", "Form(Dl(A))", "Determinantenform", "Determinante")
    lecture("form_unique", "Forms are multiples of det", "Form(F) -> evalF(F,X) = det(X)*evalF(F,E)", "Eindeutigkeit", "Determinante")
    lecture("det_T", "det of transpose", "det(A^T) = det(A)", "Transponierte", "Leibniz-Formel")

    level("D_zero1", "A Zero Column", "D(o,b) = 0",
        "A zero column kills the form. Write o as 0·o.",
        "rw< zero_smul | @o | a:=o; rw D_sm1; compute", grants = "D_sm1,D_add1", ref = "Determinantenform")
    level("D_zero2", "Zero in the Other Slot", "D(a,o) = 0",
        "Same again.", "rw< zero_smul | @o | a:=o; rw D_sm2; compute", grants = "D_sm2,D_add2", ref = "Determinantenform")
    level("D_swap", "Swapping Columns Flips the Sign", "D(a,b) = -D(b,a)",
        "The classic: expand Δ(a + b, a + b) = 0. Drag “Equal columns” into Given and pick a + b.",
        "have D_alt | a:=a + b; rw D_add1 | in h1; rw D_add2 | in h1; rw D_add2 | in h1; rw D_alt | in h1; rw D_alt | in h1; compute",
        grants = "D_alt", offers = "a + b", ref = "Determinantenform", german = "Alternierend")
    level("D_shear", "Adding a Multiple Changes Nothing", "D(a + x*b, b) = D(a,b)",
        "This is why column operations work.", "rw D_add1; rw D_sm1; rw D_alt; compute", ref = "Determinantenform", german = "Elementare Umformung")
    level("D_shear2", "Same in Slot 2", "D(a, b + x*a) = D(a,b)",
        "And in the other column.", "rw D_add2; rw D_sm2; rw D_alt; compute", ref = "Determinantenform")
    level("D_dep", "Dependent Columns Vanish", "a = x*b -> D(a,b) = 0",
        "If one column is a multiple of the other, the form is zero.",
        "intro; rw h1; rw D_sm1; rw D_alt; compute", ref = "Determinantenform")
    level("D_coef1", "Test the First Coefficient", "x*a + y*b = o -> x*D(a,b) = 0",
        "Start from Δ(o, b) = 0 and replace o using the hypothesis.",
        "intro; have D_zero1 | b:=b; rw< h1 | in h2; rw D_add1 | in h2; rw D_sm1 | in h2; rw D_sm1 | in h2; rw D_alt | in h2; compute", ref = "Determinantenform")
    level("D_coef2", "Test the Second Coefficient", "x*a + y*b = o -> y*D(a,b) = 0",
        "The same, in slot 2.",
        "intro; have D_zero2 | a:=a; rw< h1 | in h2; rw D_add2 | in h2; rw D_sm2 | in h2; rw D_sm2 | in h2; rw D_alt | in h2; compute", ref = "Determinantenform")
    level("D_indep", "Non-Zero Means Independent", "D(a,b) != 0 -> Indep2(a,b)",
        "If Δ(a, b) ≠ 0, the columns are independent. Cancel using “No Zero Divisors” for scalars.",
        "intro; rw Indep2_def; intro; split; fwd h2 D_coef1; fwd h3 K_nzd; cases h4; fwd h6 h1; fwd h2 D_coef2; fwd h8 K_nzd; cases h9; fwd h11 h1",
        grants = "K_nzd", ref = "Determinantenform")
    level("D_2x2", "BOSS: The 2×2 Formula", "D(x*e1 + y*e2, r*e1 + s*e2) = (x*s + (-(y*r)))*D(e1,e2)",
        "ad − bc, derived from nothing but the rules. Expand everything, kill equal columns, swap the leftover, then {compute}.",
        "rw D_add1; rw D_add2; rw D_add2; rw D_sm1; rw D_sm1; rw D_sm1; rw D_sm1; rw D_sm2; rw D_sm2; rw D_sm2; rw D_sm2; rw D_alt; rw D_alt; rw D_swap | @D(e2,e1); compute",
        boss = true, ref = "Determinantenform", german = "2×2-Determinante")
    level("det_mul", "The Product Formula", "det(A*B) = det(A)*det(B)",
        "X ↦ det(AX) is a determinant form, so it is a multiple of det. Write det(AB) as that form evaluated at B.",
        "rw< Dl_apply; rw form_unique; rw Dl_apply; rw M_E_r; compute; apply Dl_form",
        grants = "Dl_apply,Dl_form,form_unique,det_E", ref = "Multiplikationssatz", german = "Determinantenmultiplikationssatz")
    level("det_comm", "det(AB) = det(BA)", "det(A*B) = det(B*A)",
        "Matrices do not commute, but their determinants are scalars.", "rw det_mul; rw det_mul; compute", ref = "Multiplikationssatz")
    level("det_inv", "det of an Inverse, Times det", "Inv(A) -> det(A^-1)*det(A) = 1",
        "Use the product formula backwards.", "intro; rw< det_mul; rw inv_l; rw det_E", ref = "Multiplikationssatz")
    level("det_nonzero", "Invertible Means Non-Zero", "Inv(A) -> det(A) != 0",
        "If det A were 0, the previous level would give 0 = 1.",
        "intro; fwd h1 det_inv; rw h2 | in h3; rw K_mul0 | in h3; fwd h3 K_zero_ne",
        grants = "K_mul0,K_zero_ne", ref = "Multiplikationssatz")
    level("det_inv_formula", "det(A⁻¹) = (det A)⁻¹", "Inv(A) -> det(A^-1) = (det(A))^-1",
        "Solve the equation from two levels ago.",
        "intro; apply K_eq_inv; apply det_nonzero; apply det_inv", grants = "K_eq_inv", ref = "Multiplikationssatz")
    level("det_similar", "Similar Matrices Share a det", "Inv(P) -> det(P*(A*P^-1)) = det(A)",
        "So the determinant of a linear map is well defined, whatever the basis.",
        "intro; rw det_comm; rw M_assoc; rw inv_l; rw M_E_r", ref = "Koordinatenwechsel")
    level("det_AAT", "A Times Its Transpose", "det(A*A^T) = det(A)*det(A)",
        "The transpose rule comes from the Leibniz formula, so it is a gift.",
        "rw det_mul; rw det_T", grants = "det_T", ref = "Leibniz-Formel")
    level("det_orthogonal", "FINAL BOSS: Orthogonal Matrices", "A^T*A = E -> det(A) = 1 | det(A) = -1",
        "The summit. Start from det E = 1, replace E using the hypothesis, and finish with “Square is one”.",
        "intro; have det_E; rw< h1 | in h2; rw det_mul | in h2; rw det_T | in h2; fwd h2 K_sq1",
        grants = "K_sq1", boss = true, ref = "Determinanten", german = "Orthogonale Matrizen")
}
