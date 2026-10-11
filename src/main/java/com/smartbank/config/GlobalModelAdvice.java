package com.smartbank.config;

import com.smartbank.util.CurrencyUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAdvice {

    private final CurrencyUtil currencyUtil;
    private final com.smartbank.util.MaskUtil maskUtil;

    @ModelAttribute("currencyUtil")
    public CurrencyUtil currencyUtil() {
        return currencyUtil;
    }

    @ModelAttribute("maskUtil")
    public com.smartbank.util.MaskUtil maskUtil() {
        return maskUtil;
    }
}
