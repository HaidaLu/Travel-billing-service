package com.navan.billing.transaction.api;

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
import com.navan.billing.transaction.service.TransactionService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/transactions")
class TransactionController {

    private final TransactionService service;

    TransactionController(TransactionService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    TransactionResponse create(@Valid @RequestBody CreateTransactionRequest request) {
        return TransactionResponse.from(service.create(request.toNewTransaction()));
    }

    @GetMapping("/{id}")
    TransactionResponse get(@PathVariable String id) {
        return TransactionResponse.from(service.get(PublicId.TRANSACTION.decode(id)));
    }

    @GetMapping
    Page<TransactionResponse> list(@RequestParam(required = false) Integer limit,
                                   @RequestParam(required = false) String cursor) {
        return service.list(PageRequest.of(limit, cursor)).map(TransactionResponse::from);
    }

    @PatchMapping("/{id}")
    TransactionResponse patch(@PathVariable String id, @Valid @RequestBody PatchTransactionRequest request) {
        return TransactionResponse.from(service.patch(PublicId.TRANSACTION.decode(id), request));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        service.delete(PublicId.TRANSACTION.decode(id));
    }
}
