package pyc.lopatuxin.investment.service.market;

import pyc.lopatuxin.investment.entity.enums.SecurityType;

import java.util.Map;

/**
 * Provides default sector names for security types that do not have a sector in MOEX ISS.
 */
public final class SectorDefaults {

    public static final String CORPORATE_BONDS = "Корпоративные облигации";
    public static final String GOVERNMENT_BONDS = "Государственные облигации";

    // Static ticker-to-sector dictionary for popular Russian securities.
    // MOEX ISS /securities/{ticker}.json does not return a sector field for stocks,
    // so this map acts as a local fallback.
    public static final Map<String, String> KNOWN_SECTORS = Map.ofEntries(
            // Финансы
            Map.entry("SBER",  "Финансы"),
            Map.entry("SBERP", "Финансы"),
            Map.entry("VTBR",  "Финансы"),
            Map.entry("BSPB",  "Финансы"),
            Map.entry("CBOM",  "Финансы"),
            Map.entry("MOEX",  "Финансы"),
            Map.entry("T",     "Финансы"),
            Map.entry("AFKS",  "Финансы"),
            // Нефть и газ
            Map.entry("GAZP",  "Нефть и газ"),
            Map.entry("LKOH",  "Нефть и газ"),
            Map.entry("ROSN",  "Нефть и газ"),
            Map.entry("NVTK",  "Нефть и газ"),
            Map.entry("TATN",  "Нефть и газ"),
            Map.entry("TATNP", "Нефть и газ"),
            Map.entry("SNGS",  "Нефть и газ"),
            Map.entry("SNGSP", "Нефть и газ"),
            Map.entry("TRNFP", "Нефть и газ"),
            // Металлы и добыча
            Map.entry("CHMF",  "Металлы и добыча"),
            Map.entry("NLMK",  "Металлы и добыча"),
            Map.entry("MAGN",  "Металлы и добыча"),
            Map.entry("GMKN",  "Металлы и добыча"),
            Map.entry("RUAL",  "Металлы и добыча"),
            Map.entry("ALRS",  "Металлы и добыча"),
            Map.entry("PLZL",  "Металлы и добыча"),
            Map.entry("SELG",  "Металлы и добыча"),
            Map.entry("MTLR",  "Металлы и добыча"),
            Map.entry("MTLRP", "Металлы и добыча"),
            Map.entry("RASP",  "Металлы и добыча"),
            // Электроэнергетика
            Map.entry("FEES",  "Электроэнергетика"),
            Map.entry("IRAO",  "Электроэнергетика"),
            Map.entry("HYDR",  "Электроэнергетика"),
            Map.entry("UPRO",  "Электроэнергетика"),
            Map.entry("ENPG",  "Электроэнергетика"),
            // Потребительский сектор
            Map.entry("MGNT",  "Потребительский сектор"),
            Map.entry("X5",    "Потребительский сектор"),
            Map.entry("LENT",  "Потребительский сектор"),
            Map.entry("MVID",  "Потребительский сектор"),
            Map.entry("OZON",  "Потребительский сектор"),
            Map.entry("BELU",  "Потребительский сектор"),
            // Телеком
            Map.entry("MTSS",  "Телеком"),
            Map.entry("RTKM",  "Телеком"),
            Map.entry("RTKMP", "Телеком"),
            // Информационные технологии
            Map.entry("VKCO",  "Информационные технологии"),
            Map.entry("YDEX",  "Информационные технологии"),
            Map.entry("HEAD",  "Информационные технологии"),
            Map.entry("POSI",  "Информационные технологии"),
            Map.entry("ASTR",  "Информационные технологии"),
            Map.entry("WUSH",  "Информационные технологии"),
            // Транспорт
            Map.entry("AFLT",  "Транспорт"),
            Map.entry("FLOT",  "Транспорт"),
            Map.entry("FESH",  "Транспорт"),
            Map.entry("DELI",  "Транспорт"),
            // Химия
            Map.entry("AKRN",  "Химия"),
            Map.entry("PHOR",  "Химия"),
            // Лесопереработка
            Map.entry("SGZH",  "Лесопереработка"),
            // Строительство
            Map.entry("PIKK",  "Строительство"),
            Map.entry("SMLT",  "Строительство"),
            Map.entry("ETLN",  "Строительство"),
            // Здравоохранение
            Map.entry("MDMG",  "Здравоохранение"),
            Map.entry("GEMC",  "Здравоохранение")
    );

    private SectorDefaults() {
    }

    /**
     * Returns the default sector for a given security type, or {@code null} if no default applies.
     */
    public static String defaultSectorFor(SecurityType type) {
        if (type == null) return null;
        return switch (type) {
            case BOND -> CORPORATE_BONDS;
            case OFZ -> GOVERNMENT_BONDS;
            default -> null;
        };
    }

    /**
     * Resolves sector for a ticker: first checks the local dictionary,
     * then falls back to the type-based default (covers BOND/OFZ).
     */
    public static String resolveSector(String ticker, SecurityType type) {
        if (ticker != null) {
            String known = KNOWN_SECTORS.get(ticker.toUpperCase());
            if (known != null) {
                return known;
            }
        }
        return defaultSectorFor(type);
    }
}
