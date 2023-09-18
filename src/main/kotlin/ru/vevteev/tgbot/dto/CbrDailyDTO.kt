package ru.vevteev.tgbot.dto

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement
import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import java.math.BigDecimal
import java.time.LocalDate


//@XmlRootElement(name = "ValCurs")
@JacksonXmlRootElement(localName = "ValCurs")
@JsonIgnoreProperties("name")
data class CbrDailyDTO(
    @JacksonXmlProperty(localName = "Valute")
    @JacksonXmlElementWrapper(useWrapping = false)
    var valCurs: List<CbrDailyValuteDTO> = emptyList(),
    @JacksonXmlProperty(localName = "Date", isAttribute = true)
    @JsonFormat(pattern = "dd.MM.yyyy")
    var date: LocalDate,
) {
    fun numCodeToCharCode(numCode: String) = valCurs.map { it.numCode to it.charCode }.find { it.first == numCode }?.second
}

@XmlAccessorType(XmlAccessType.FIELD)
data class CbrDailyValuteDTO(
    @JacksonXmlProperty(localName = "ID", isAttribute = true)
    var id: String? = "",
    @JacksonXmlProperty(localName = "NumCode")
    var numCode: String? = "0",
    @JacksonXmlProperty(localName = "CharCode")
    var charCode: String? = "",
    @JacksonXmlProperty(localName = "Nominal")
    var nominal: Int? = 0,
    @JacksonXmlProperty(localName = "Name")
    val name: String? = "",
    @JacksonXmlProperty(localName = "Value")
    val value: BigDecimal? = BigDecimal.ZERO
) {
    constructor() : this("", "","", 0, "", BigDecimal.ZERO)
}