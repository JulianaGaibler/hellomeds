// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 HelloMeds Contributors

package me.juliana.hellomeds.domain.ml

import me.juliana.hellomeds.data.model.enums.MedicationStrengthUnit
import me.juliana.hellomeds.data.model.enums.MedicationType

/**
 * Dictionaries and constants for medication detection heuristics.
 *
 * This file contains reference data used by the fallback heuristic when on-device AI
 * is unavailable. The heuristic uses pattern matching, fuzzy string matching, and
 * pharmaceutical naming conventions to extract medication information from OCR text.
 */
object MedicationDictionaries {

    /**
     * Maximum number of medication name suggestions to return.
     */
    const val MAX_NAME_SUGGESTIONS = 4

    /**
     * Maximum number of medication type suggestions to return.
     */
    const val MAX_TYPE_SUGGESTIONS = 3

    /**
     * Minimum token length to consider for medication name extraction.
     * Tokens shorter than this are likely abbreviations or noise.
     */
    const val MIN_TOKEN_LENGTH = 4

    /**
     * Common medications for exact/fuzzy matching. Includes EN/US, UK, DE, FR, ES, PT variants.
     */
    val KNOWN_MEDICATIONS = setOf(
        "acetaminophen",
        "albuterol",
        "allopurinol",
        "amlodipine",
        "amoxicilina",
        "amoxicillin",
        "amoxicilline",
        "apixaban",
        "aspirin",
        "atenolol",
        "atorvastatin",
        "azithromycin",
        "azithromycine",
        "bisoprolol",
        "candesartan",
        "carvedilol",
        "cephalexin",
        "cetirizine",
        "ciprofloxacin",
        "citalopram",
        "clindamycin",
        "clopidogrel",
        "co-amoxiclav",
        "co-codamol",
        "codeine",
        "colchicine",
        "diclofenac",
        "diphenhydramine",
        "doxycycline",
        "duloxetine",
        "enalapril",
        "escitalopram",
        "esomeprazole",
        "estradiol",
        "estrogen",
        "estrógeno",
        "fexofenadine",
        "fluoxetine",
        "furosemide",
        "gabapentin",
        "gliclazide",
        "glimepiride",
        "hydrochlorothiazide",
        "hydrocodone",
        "hydrocortisone",
        "ibuprofen",
        "ibuprofène",
        "ibuprofeno",
        "insulin",
        "lansoprazole",
        "levofloxacin",
        "levothyroxine",
        "lisinopril",
        "loratadine",
        "losartan",
        "metamizol",
        "metformin",
        "metoprolol",
        "montelukast",
        "morphine",
        "naproxen",
        "omeprazol",
        "omeprazole",
        "oxycodone",
        "pantoprazole",
        "paracetamol",
        "paracétamol",
        "perindopril",
        "pravastatin",
        "prednisolone",
        "prednisone",
        "pregabalin",
        "rabeprazole",
        "ramipril",
        "rivaroxaban",
        "rosuvastatin",
        "salbutamol",
        "sertraline",
        "simvastatin",
        "spironolactone",
        "telmisartan",
        "testosterona",
        "testosterone",
        "tramadol",
        "valsartan",
        "venlafaxine",
        "vitamin c",
        "vitamin d",
        "vitamin d3",
        "voltaren",
        "warfarin",
    )

    /**
     * Pharmaceutical suffixes by drug class. Includes EN, ES/PT, FR variants.
     */
    val MEDICATION_SUFFIXES = listOf(
        "ane",
        "ate",
        "azine",
        "barbital",
        "buterol",
        "cef",
        "cilina",
        "cillin",
        "cilline",
        "conazole",
        "dazole",
        "dipine",
        "dronate",
        "estatina",
        "floxacin",
        "floxacina",
        "floxacine",
        "floxacino",
        "ide",
        "ine",
        "iol",
        "mab",
        "micina",
        "mycin",
        "mycine",
        "nib",
        "olol",
        "one",
        "pam",
        "phylline",
        "prazol",
        "prazole",
        "pril",
        "sartan",
        "sartán",
        "semide",
        "solone",
        "son",
        "sone",
        "statin",
        "terol",
        "thiazide",
        "tidine",
        "triptan",
        "vir",
        "zepam",
        "zolam",
    )

    /**
     * Label words that are NOT medications. Penalized to avoid false positives. EN/DE/FR/ES/PT.
     */
    val PENALIZED_WORDS = setOf(
        "abends",
        "apotheke",
        "arzt",
        "ärztin",
        "aufbewahren",
        "auxiliary",
        "bedtime",
        "brand",
        "cápsula",
        "cápsulas",
        "capsule",
        "capsules",
        "caution",
        "chemist",
        "chew",
        "children",
        "comprimé",
        "comprimés",
        "comprimido",
        "comprimidos",
        "conservar",
        "conserver",
        "crianças",
        "crush",
        "daily",
        "delayed",
        "diaria",
        "diária",
        "diario",
        "diário",
        "directions",
        "discard",
        "dispose",
        "docteur",
        "doctor",
        "dosage",
        "dose",
        "dosis",
        "doutor",
        "einnahme",
        "enfants",
        "evening",
        "expiration",
        "expire",
        "expiry",
        "extended",
        "farmacia",
        "farmácia",
        "food",
        "gélule",
        "gélules",
        "generic",
        "guardar",
        "hydrochloride",
        "instructions",
        "journalier",
        "kapsel",
        "kapseln",
        "keep",
        "kinder",
        "mañana",
        "manhã",
        "matin",
        "meal",
        "meals",
        "médecin",
        "médico",
        "modified",
        "morgens",
        "morning",
        "nehmen",
        "nicht",
        "night",
        "niños",
        "noche",
        "noite",
        "once",
        "ordonnance",
        "packung",
        "patient",
        "pharmacie",
        "pharmacist",
        "pharmacy",
        "physician",
        "píldora",
        "píldoras",
        "pill",
        "pille",
        "pillen",
        "pills",
        "pílula",
        "pílulas",
        "pilule",
        "pilules",
        "posologie",
        "prendre",
        "prescriber",
        "prescription",
        "prise",
        "quantity",
        "quotidien",
        "reach",
        "receita",
        "receta",
        "refill",
        "refills",
        "refrigerate",
        "release",
        "rezept",
        "soir",
        "storage",
        "store",
        "swallow",
        "tablet",
        "tableta",
        "tabletas",
        "tablets",
        "tablette",
        "tabletten",
        "täglich",
        "take",
        "tarde",
        "times",
        "toma",
        "tomar",
        "twice",
        "warning",
        "water",
    )

    /**
     * Allowed medication types (dosage forms).
     *
     * This list is used by both on-device AI (as a constraint) and the fallback heuristic
     * for medication type detection. Derived from MedicationType enum.
     */
    val ALLOWED_TYPES = MedicationType.allValues()

    /**
     * Allowed dosage units.
     *
     * This list is used by both on-device AI (as a constraint) and the fallback heuristic
     * for strength extraction. Derived from MedicationStrengthUnit enum (lowercase for OCR matching).
     *
     * Units:
     * - mg: milligrams (most common)
     * - ml: milliliters (liquids, lowercase for OCR compatibility)
     * - mcg: micrograms
     * - g: grams
     * - iu: International Units (vitamins, lowercase for OCR compatibility)
     * - %: percentage (topicals, solutions)
     */
    val ALLOWED_UNITS = MedicationStrengthUnit.allLowercaseValues()
}
