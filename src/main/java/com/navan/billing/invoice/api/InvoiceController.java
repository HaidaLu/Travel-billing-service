package com.navan.billing.invoice.api;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.navan.billing.common.id.PublicId;
import com.navan.billing.common.page.Page;
import com.navan.billing.common.page.PageRequest;
import com.navan.billing.invoice.service.InvoiceService;

@RestController
@RequestMapping("/invoices")
class InvoiceController {

    private final InvoiceService service;

    InvoiceController(InvoiceService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    InvoiceResponse create(@RequestBody InvoiceRequest request) {
        return InvoiceResponse.from(service.create(request.rowIds()));
    }

    @GetMapping("/{id}")
    InvoiceResponse get(@PathVariable String id) {
        return InvoiceResponse.from(service.get(PublicId.INVOICE.decode(id)));
    }

    @GetMapping
    Page<InvoiceResponse> list(@RequestParam(required = false) Integer limit,
                               @RequestParam(required = false) String cursor) {
        return service.list(PageRequest.of(limit, cursor)).map(InvoiceResponse::from);
    }

    @PatchMapping("/{id}")
    InvoiceResponse replace(@PathVariable String id, @RequestBody InvoiceRequest request) {
        return InvoiceResponse.from(service.replace(PublicId.INVOICE.decode(id), request.rowIds()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        service.delete(PublicId.INVOICE.decode(id));
    }
}
