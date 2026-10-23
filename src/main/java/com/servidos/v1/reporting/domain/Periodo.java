package com.servidos.v1.reporting.domain;

import com.servidos.v1.shared.exception.BusinessException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Período calendario de un reporte: el que contiene la fecha de referencia. */
public enum Periodo {
    DIARIO,
    /** De lunes a domingo. */
    SEMANAL,
    MENSUAL;

    public RangoFechas rango(LocalDate fecha) {
        if (fecha == null) throw new BusinessException("La fecha es obligatoria");
        return switch (this) {
            case DIARIO -> new RangoFechas(fecha, fecha);
            case SEMANAL -> new RangoFechas(
                    fecha.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                    fecha.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)));
            case MENSUAL -> new RangoFechas(
                    fecha.with(TemporalAdjusters.firstDayOfMonth()),
                    fecha.with(TemporalAdjusters.lastDayOfMonth()));
        };
    }
}
